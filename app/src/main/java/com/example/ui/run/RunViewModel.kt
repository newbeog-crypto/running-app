package com.example.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.WorkoutType
import com.example.data.repository.ActivityRepository
import com.example.sensors.GpsSignalState
import com.example.service.TrackingSessionController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SessionState {
    IDLE,
    RUNNING,
    PAUSED
}

data class RunUiState(
    val workoutType: WorkoutType = WorkoutType.RUN,
    val sessionState: SessionState = SessionState.IDLE,
    val elapsedMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentPaceSecPerKm: Int = 0,
    val averagePaceSecPerKm: Int = 0,
    val caloriesBurned: Int = 0,
    val gpsSignalState: GpsSignalState = GpsSignalState.IDLE,
    val locationPermissionGranted: Boolean = false,
    val wasLocationPermissionAsked: Boolean = false,
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
    val lastSavedSessionId: String? = null
)

class RunViewModel(
    private val repository: ActivityRepository,
    private val trackingController: TrackingSessionController,
    private val currentUserId: String?
) : ViewModel() {

    val uiState: StateFlow<RunUiState> = combine(
        trackingController.workoutState,
        repository.wasLocationPermissionAsked
    ) { snapshot, wasAsked ->
        RunUiState(
            workoutType = snapshot.workoutType,
            sessionState = snapshot.sessionState,
            elapsedMillis = snapshot.elapsedMillis,
            distanceMeters = snapshot.distanceMeters,
            currentPaceSecPerKm = snapshot.currentPaceSecPerKm,
            averagePaceSecPerKm = snapshot.averagePaceSecPerKm,
            caloriesBurned = snapshot.caloriesBurned,
            gpsSignalState = snapshot.gpsSignalState,
            locationPermissionGranted = snapshot.locationPermissionGranted,
            wasLocationPermissionAsked = wasAsked,
            isSaving = snapshot.isSaving,
            saveErrorMessage = snapshot.saveErrorMessage,
            lastSavedSessionId = snapshot.lastSavedSessionId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RunUiState()
    )

    init {
        trackingController.bindUser(currentUserId)
    }

    fun selectWorkoutType(type: WorkoutType) {
        trackingController.selectWorkoutType(type)
    }

    fun onLocationPermissionStateChanged(granted: Boolean) {
        trackingController.onLocationPermissionChanged(granted)
    }

    fun markLocationPermissionAsked() {
        viewModelScope.launch {
            repository.markLocationPermissionAsked()
        }
    }

    fun startSession() {
        trackingController.startWorkout(currentUserId)
    }

    fun pauseSession() {
        trackingController.pauseWorkout()
    }

    fun resumeSession() {
        trackingController.resumeWorkout()
    }

    fun finishAndSaveSession() {
        trackingController.finishAndSaveWorkout()
    }

    fun discardSession() {
        trackingController.discardWorkout()
    }

    fun consumeSavedEvent() {
        trackingController.consumeSavedEvent()
    }

    class Factory(
        private val repository: ActivityRepository,
        private val trackingController: TrackingSessionController,
        private val currentUserId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RunViewModel(repository, trackingController, currentUserId) as T
        }
    }
}
