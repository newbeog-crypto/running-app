package com.example.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DailyStepsEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CloudFitnessRepository
import com.example.data.repository.ActivityRepository
import com.example.sensors.StepCounterManager
import com.example.service.TrackingSessionController
import com.example.util.DateUtils
import com.example.util.MetricUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class DailyStepBar(
    val dateKey: String,
    val dayLabel: String,
    val steps: Int,
    val goalSteps: Int,
    val isToday: Boolean
)

data class TodayUiState(
    val todaySteps: Int = 0,
    val dailyGoal: Int = UserPreferencesRepository.DEFAULT_STEP_GOAL,
    val completionFraction: Float = 0f,
    val completionPercent: Int = 0,
    val estimatedDistanceKm: Double = 0.0,
    val activeCalories: Int = 0,
    val hasHardwareStepSensor: Boolean = false,
    val isBackgroundTrackingEnabled: Boolean = false,
    val activityPermissionGranted: Boolean = false,
    val wasActivityPermissionAsked: Boolean = false,
    val recentDays: List<DailyStepBar> = emptyList()
)

class TodayViewModel(
    private val repository: ActivityRepository,
    private val cloudRepository: CloudFitnessRepository,
    private val stepCounterManager: StepCounterManager,
    private val trackingController: TrackingSessionController,
    private val currentUserId: String?
) : ViewModel() {

    private val _activityPermissionGranted = MutableStateFlow(false)

    val uiState: StateFlow<TodayUiState> = combine(
        repository.observeTodaySteps(),
        repository.dailyStepGoal,
        repository.observeRecentDailySteps(7),
        repository.wasActivityPermissionAsked,
        repository.backgroundStepsEnabled,
        _activityPermissionGranted
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val todayEntity = values[0] as DailyStepsEntity
        val goal = values[1] as Int
        @Suppress("UNCHECKED_CAST")
        val recentEntities = values[2] as List<DailyStepsEntity>
        val permAsked = values[3] as Boolean
        val bgEnabled = values[4] as Boolean
        val permGranted = values[5] as Boolean

        val hasHardware = stepCounterManager.hasHardwareStepSensor
        val steps = todayEntity.steps
        val safeGoal = goal.coerceAtLeast(1)
        val fraction = (steps.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
        val percent = ((steps.toFloat() / safeGoal.toFloat()) * 100f).roundToInt().coerceAtLeast(0)

        val entityByDate = recentEntities.associateBy { it.dateKey }
        val todayKey = DateUtils.todayDateKey()
        val bars = DateUtils.lastSevenDateKeys().map { key ->
            val match = entityByDate[key]
            DailyStepBar(
                dateKey = key,
                dayLabel = DateUtils.formatShortDayFromKey(key),
                steps = if (key == todayKey) steps else (match?.steps ?: 0),
                goalSteps = if (key == todayKey) safeGoal else (match?.goalSteps ?: safeGoal),
                isToday = key == todayKey
            )
        }

        TodayUiState(
            todaySteps = steps,
            dailyGoal = safeGoal,
            completionFraction = fraction,
            completionPercent = percent,
            estimatedDistanceKm = MetricUtils.estimateDistanceKmFromSteps(steps),
            activeCalories = MetricUtils.estimateCaloriesFromSteps(steps),
            hasHardwareStepSensor = hasHardware,
            isBackgroundTrackingEnabled = bgEnabled,
            activityPermissionGranted = permGranted,
            wasActivityPermissionAsked = permAsked,
            recentDays = bars
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TodayUiState(
            hasHardwareStepSensor = stepCounterManager.hasHardwareStepSensor
        )
    )

    init {
        trackingController.bindUser(currentUserId)
        trackingController.onAppForegrounded()
        viewModelScope.launch {
            repository.ensureTodayRowExists()
        }
    }

    fun onActivityPermissionStateChanged(granted: Boolean) {
        _activityPermissionGranted.value = granted
        trackingController.onActivityPermissionChanged(granted)
        if (granted) {
            trackingController.onAppForegrounded()
        }
    }

    fun markActivityPermissionAsked() {
        viewModelScope.launch {
            repository.markActivityPermissionAsked()
        }
    }

    fun updateDailyGoal(newGoal: Int) {
        viewModelScope.launch {
            repository.updateDailyGoal(newGoal)
            if (currentUserId != null) {
                cloudRepository.updateCloudDailyGoal(currentUserId, newGoal)
            }
        }
    }

    fun setBackgroundStepsEnabled(enabled: Boolean) {
        trackingController.setBackgroundStepsEnabled(enabled)
    }

    class Factory(
        private val repository: ActivityRepository,
        private val cloudRepository: CloudFitnessRepository,
        private val stepCounterManager: StepCounterManager,
        private val trackingController: TrackingSessionController,
        private val currentUserId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TodayViewModel(
                repository = repository,
                cloudRepository = cloudRepository,
                stepCounterManager = stepCounterManager,
                trackingController = trackingController,
                currentUserId = currentUserId
            ) as T
        }
    }
}
