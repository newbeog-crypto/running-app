package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.data.model.RoutePoint
import com.example.data.model.WorkoutType
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CloudFitnessRepository
import com.example.data.repository.ActivityRepository
import com.example.sensors.GpsSignalState
import com.example.sensors.LocationFix
import com.example.sensors.LocationTrackerManager
import com.example.sensors.StepCounterManager
import com.example.ui.run.SessionState
import com.example.util.DateUtils
import com.example.util.MetricUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class WorkoutTrackingSnapshot(
    val activeUserId: String? = null,
    val workoutType: WorkoutType = WorkoutType.RUN,
    val sessionState: SessionState = SessionState.IDLE,
    val elapsedMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentPaceSecPerKm: Int = 0,
    val averagePaceSecPerKm: Int = 0,
    val caloriesBurned: Int = 0,
    val gpsSignalState: GpsSignalState = GpsSignalState.IDLE,
    val locationPermissionGranted: Boolean = false,
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
    val lastSavedSessionId: String? = null
)

class TrackingSessionController(
    private val appContext: Context,
    private val activityRepository: ActivityRepository,
    private val cloudRepository: CloudFitnessRepository,
    private val preferences: UserPreferencesRepository,
    private val stepCounterManager: StepCounterManager,
    private val locationTrackerManager: LocationTrackerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _workoutState = MutableStateFlow(WorkoutTrackingSnapshot())
    val workoutState: StateFlow<WorkoutTrackingSnapshot> = _workoutState.asStateFlow()

    private val _backgroundStepsActive = MutableStateFlow(false)
    val backgroundStepsActive: StateFlow<Boolean> = _backgroundStepsActive.asStateFlow()

    private var timerJob: Job? = null
    private var locationJob: Job? = null
    private var hardwareStepJob: Job? = null
    private var stepCloudSyncJob: Job? = null

    private var activeUserId: String? = null
    private var sessionStartedAtWallMillis: Long = 0L
    private var activeSegmentStartedRealtimeMillis: Long = 0L
    private var accumulatedActiveMillis: Long = 0L

    // Transient single previous point used strictly in memory for incremental distance calculation; never persisted.
    private var lastFixPoint: RoutePoint? = null

    private var lastSyncedSteps: Int = -1
    private var lastSyncedGoal: Int = -1

    init {
        scope.launch {
            preferences.backgroundStepsEnabled.collect { enabled ->
                _backgroundStepsActive.value = enabled
                if (enabled) {
                    ensureHardwareStepObservation()
                }
                syncForegroundServiceState()
            }
        }
    }

    fun bindUser(userId: String?) {
        activeUserId = userId
        if (_workoutState.value.sessionState == SessionState.IDLE) {
            _workoutState.update { it.copy(activeUserId = userId) }
        }
        if (userId != null) {
            if (stepCloudSyncJob == null) {
                startPeriodicCloudStepSync()
            }
        } else {
            stepCloudSyncJob?.cancel()
            stepCloudSyncJob = null
        }
    }

    fun onAppForegrounded() {
        if (_backgroundStepsActive.value && stepCounterManager.hasHardwareStepSensor && hasActivityPermission()) {
            ensureHardwareStepObservation()
        }
        syncForegroundServiceState()
    }

    fun unbindUser() {
        activeUserId = null
        if (_workoutState.value.sessionState != SessionState.IDLE) {
            discardWorkout()
        }
        stepCloudSyncJob?.cancel()
        stepCloudSyncJob = null
    }

    fun selectWorkoutType(type: WorkoutType) {
        if (_workoutState.value.sessionState != SessionState.IDLE) return
        _workoutState.update { it.copy(workoutType = type) }
    }

    fun onLocationPermissionChanged(granted: Boolean) {
        _workoutState.update { current ->
            val signal = when {
                !granted -> GpsSignalState.PERMISSION_REQUIRED
                current.sessionState == SessionState.RUNNING && current.gpsSignalState == GpsSignalState.PERMISSION_REQUIRED ->
                    GpsSignalState.SEARCHING
                current.sessionState == SessionState.IDLE -> GpsSignalState.IDLE
                else -> current.gpsSignalState
            }
            current.copy(locationPermissionGranted = granted, gpsSignalState = signal)
        }
        val state = _workoutState.value
        if (granted && state.sessionState == SessionState.RUNNING) {
            startLocationUpdates()
            syncForegroundServiceState()
        }
    }

    fun startWorkout(userId: String?) {
        activeUserId = userId
        val current = _workoutState.value
        if (current.sessionState != SessionState.IDLE) return

        sessionStartedAtWallMillis = System.currentTimeMillis()
        activeSegmentStartedRealtimeMillis = SystemClock.elapsedRealtime()
        accumulatedActiveMillis = 0L
        lastFixPoint = null

        val initialSignal = if (current.locationPermissionGranted) {
            GpsSignalState.SEARCHING
        } else {
            GpsSignalState.PERMISSION_REQUIRED
        }

        _workoutState.update {
            it.copy(
                activeUserId = userId,
                sessionState = SessionState.RUNNING,
                elapsedMillis = 0L,
                distanceMeters = 0.0,
                currentPaceSecPerKm = 0,
                averagePaceSecPerKm = 0,
                caloriesBurned = 0,
                gpsSignalState = initialSignal,
                saveErrorMessage = null,
                lastSavedSessionId = null
            )
        }

        startTimer()
        if (current.locationPermissionGranted) {
            startLocationUpdates()
        }
        syncForegroundServiceState()
    }

    fun pauseWorkout() {
        if (_workoutState.value.sessionState != SessionState.RUNNING) return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulatedActiveMillis += (nowRealtime - activeSegmentStartedRealtimeMillis).coerceAtLeast(0L)
        timerJob?.cancel()
        locationJob?.cancel()
        lastFixPoint = null

        _workoutState.update {
            it.copy(
                sessionState = SessionState.PAUSED,
                elapsedMillis = accumulatedActiveMillis,
                currentPaceSecPerKm = 0
            )
        }
        syncForegroundServiceState()
    }

    fun resumeWorkout() {
        val current = _workoutState.value
        if (current.sessionState != SessionState.PAUSED) return
        activeSegmentStartedRealtimeMillis = SystemClock.elapsedRealtime()
        lastFixPoint = null

        val nextSignal = if (current.locationPermissionGranted) {
            GpsSignalState.SEARCHING
        } else {
            GpsSignalState.PERMISSION_REQUIRED
        }

        _workoutState.update {
            it.copy(
                sessionState = SessionState.RUNNING,
                gpsSignalState = nextSignal
            )
        }
        startTimer()
        if (current.locationPermissionGranted) {
            startLocationUpdates()
        }
        syncForegroundServiceState()
    }

    fun finishAndSaveWorkout() {
        val snapshot = _workoutState.value
        if (snapshot.sessionState == SessionState.IDLE || snapshot.isSaving) return

        val finalElapsed = if (snapshot.sessionState == SessionState.RUNNING) {
            accumulatedActiveMillis + (SystemClock.elapsedRealtime() - activeSegmentStartedRealtimeMillis).coerceAtLeast(0L)
        } else {
            accumulatedActiveMillis
        }

        timerJob?.cancel()
        locationJob?.cancel()
        lastFixPoint = null

        _workoutState.update { it.copy(isSaving = true, saveErrorMessage = null) }

        scope.launch {
            val startedAt = if (sessionStartedAtWallMillis > 0L) {
                sessionStartedAtWallMillis
            } else {
                System.currentTimeMillis() - finalElapsed
            }
            val endedAt = maxOf(System.currentTimeMillis(), startedAt)
            val avgPace = MetricUtils.calculateAveragePaceSecPerKm(snapshot.distanceMeters, finalElapsed)
            val calories = MetricUtils.calculateWorkoutCalories(snapshot.distanceMeters, finalElapsed, snapshot.workoutType)

            val localRowId = activityRepository.saveWorkoutSession(
                type = snapshot.workoutType,
                startedAtMillis = startedAt,
                endedAtMillis = endedAt,
                movingTimeMillis = finalElapsed,
                distanceMeters = snapshot.distanceMeters
            )

            val uid = snapshot.activeUserId ?: activeUserId
            if (uid != null) {
                val workoutId = "w_$startedAt"
                val cloudResult = cloudRepository.saveCloudWorkout(
                    userId = uid,
                    workoutId = workoutId,
                    type = snapshot.workoutType,
                    startedAtMillis = startedAt,
                    endedAtMillis = endedAt,
                    movingTimeMillis = finalElapsed,
                    distanceMeters = snapshot.distanceMeters,
                    averagePaceSecPerKm = avgPace,
                    caloriesBurned = calories
                )
                cloudResult.fold(
                    onSuccess = { savedId ->
                        resetWorkoutToIdle(savedSessionId = savedId, error = null)
                    },
                    onFailure = { error ->
                        resetWorkoutToIdle(
                            savedSessionId = null,
                            error = error.localizedMessage ?: "Saved locally; cloud sync failed."
                        )
                    }
                )
            } else {
                resetWorkoutToIdle(savedSessionId = localRowId.toString(), error = null)
            }
            syncForegroundServiceState()
        }
    }

    fun discardWorkout() {
        timerJob?.cancel()
        locationJob?.cancel()
        lastFixPoint = null
        resetWorkoutToIdle(savedSessionId = null, error = null)
        syncForegroundServiceState()
    }

    fun consumeSavedEvent() {
        _workoutState.update { it.copy(lastSavedSessionId = null, saveErrorMessage = null) }
    }

    fun setBackgroundStepsEnabled(enabled: Boolean) {
        scope.launch {
            activityRepository.setBackgroundStepsEnabled(enabled)
        }
        if (enabled) {
            ensureHardwareStepObservation()
        }
        _backgroundStepsActive.value = enabled
        syncForegroundServiceState()
    }

    fun onActivityPermissionChanged(granted: Boolean) {
        if (granted && stepCounterManager.hasHardwareStepSensor) {
            ensureHardwareStepObservation()
            syncForegroundServiceState()
        } else if (!granted) {
            hardwareStepJob?.cancel()
            hardwareStepJob = null
        }
    }

    private fun ensureHardwareStepObservation() {
        if (!stepCounterManager.hasHardwareStepSensor || !hasActivityPermission()) return
        if (hardwareStepJob?.isActive == true) return
        hardwareStepJob = scope.launch {
            stepCounterManager.observeRawStepCounter().collect { rawStepsSinceBoot ->
                activityRepository.recordHardwareSensorReading(rawStepsSinceBoot)
            }
        }
    }

    private fun startPeriodicCloudStepSync() {
        stepCloudSyncJob?.cancel()
        stepCloudSyncJob = scope.launch {
            activityRepository.observeTodaySteps().collect { today ->
                val uid = activeUserId ?: return@collect
                if (today.steps != lastSyncedSteps || today.goalSteps != lastSyncedGoal) {
                    lastSyncedSteps = today.steps
                    lastSyncedGoal = today.goalSteps
                    delay(1_200L)
                    cloudRepository.syncDailySteps(
                        userId = uid,
                        dateKey = DateUtils.todayDateKey(),
                        steps = today.steps,
                        goalSteps = today.goalSteps
                    )
                }
            }
        }
    }

    private fun resetWorkoutToIdle(savedSessionId: String?, error: String?) {
        accumulatedActiveMillis = 0L
        sessionStartedAtWallMillis = 0L
        activeSegmentStartedRealtimeMillis = 0L
        lastFixPoint = null
        _workoutState.update { current ->
            current.copy(
                sessionState = SessionState.IDLE,
                elapsedMillis = 0L,
                distanceMeters = 0.0,
                currentPaceSecPerKm = 0,
                averagePaceSecPerKm = 0,
                caloriesBurned = 0,
                gpsSignalState = if (!current.locationPermissionGranted) {
                    GpsSignalState.PERMISSION_REQUIRED
                } else {
                    GpsSignalState.IDLE
                },
                isSaving = false,
                saveErrorMessage = error,
                lastSavedSessionId = savedSessionId
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = accumulatedActiveMillis + (now - activeSegmentStartedRealtimeMillis).coerceAtLeast(0L)
                _workoutState.update { state ->
                    val avgPace = MetricUtils.calculateAveragePaceSecPerKm(
                        distanceMeters = state.distanceMeters,
                        durationMillis = elapsed
                    )
                    val calories = MetricUtils.calculateWorkoutCalories(
                        distanceMeters = state.distanceMeters,
                        durationMillis = elapsed,
                        type = state.workoutType
                    )
                    state.copy(
                        elapsedMillis = elapsed,
                        averagePaceSecPerKm = avgPace,
                        caloriesBurned = calories
                    )
                }
                delay(500L)
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        val state = _workoutState.value
        if (!state.locationPermissionGranted) return

        locationJob = scope.launch {
            locationTrackerManager.observeLocationFixes().collect { fix ->
                handleLocationFix(fix)
            }
        }
    }

    private fun handleLocationFix(fix: LocationFix) {
        _workoutState.update { current ->
            if (current.sessionState != SessionState.RUNNING) return@update current

            val prev = lastFixPoint
            var addedDistance = 0.0
            var segmentPace = current.currentPaceSecPerKm

            if (prev == null) {
                lastFixPoint = fix.point
            } else {
                val stepDist = MetricUtils.haversineMeters(prev, fix.point)
                val dtSeconds = ((fix.point.timestampMillis - prev.timestampMillis) / 1000.0)
                    .coerceAtLeast(0.5)
                val speedCheck = stepDist / dtSeconds
                if (stepDist >= 1.8 && speedCheck <= 25.0) {
                    addedDistance = stepDist
                    lastFixPoint = fix.point
                    if (fix.speedMetersPerSec > 0.45f) {
                        segmentPace = (1000f / fix.speedMetersPerSec).roundToInt().coerceIn(120, 1800)
                    } else if (stepDist > 2.0) {
                        segmentPace = ((dtSeconds / (stepDist / 1000.0))).roundToInt().coerceIn(120, 1800)
                    }
                }
            }

            if (fix.speedMetersPerSec > 0.45f && addedDistance == 0.0) {
                segmentPace = (1000f / fix.speedMetersPerSec).roundToInt().coerceIn(120, 1800)
            }

            val updatedDistance = current.distanceMeters + addedDistance
            val avgPace = MetricUtils.calculateAveragePaceSecPerKm(
                distanceMeters = updatedDistance,
                durationMillis = current.elapsedMillis
            )
            val calories = MetricUtils.calculateWorkoutCalories(
                distanceMeters = updatedDistance,
                durationMillis = current.elapsedMillis,
                type = current.workoutType
            )

            current.copy(
                distanceMeters = updatedDistance,
                currentPaceSecPerKm = segmentPace,
                averagePaceSecPerKm = avgPace,
                caloriesBurned = calories,
                gpsSignalState = GpsSignalState.ACTIVE_FIX
            )
        }
    }

    fun shouldRunForegroundService(): Boolean {
        val workoutActive = _workoutState.value.sessionState != SessionState.IDLE
        val autoBgSteps = _backgroundStepsActive.value &&
            stepCounterManager.hasHardwareStepSensor &&
            hasActivityPermission()
        return workoutActive || autoBgSteps
    }

    fun needsWakeLock(): Boolean {
        return _workoutState.value.sessionState == SessionState.RUNNING
    }

    private fun hasActivityPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun syncForegroundServiceState() {
        TrackingForegroundService.sync(appContext, shouldRunForegroundService())
    }
}
