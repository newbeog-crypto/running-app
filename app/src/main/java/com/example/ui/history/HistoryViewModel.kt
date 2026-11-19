package com.example.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.WorkoutType
import com.example.data.remote.CloudDailySteps
import com.example.data.remote.CloudFitnessRepository
import com.example.data.remote.CloudWorkoutSession
import com.example.data.remote.UiState
import com.example.data.repository.ActivityRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WorkoutFilter(val label: String) {
    ALL("All"),
    RUN("Runs"),
    WALK("Walks")
}

data class HistoryData(
    val sessions: List<CloudWorkoutSession> = emptyList(),
    val filteredSessions: List<CloudWorkoutSession> = emptyList(),
    val selectedFilter: WorkoutFilter = WorkoutFilter.ALL,
    val recentDailySteps: List<CloudDailySteps> = emptyList(),
    val totalWorkoutsCount: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalMovingTimeMillis: Long = 0L
)

class HistoryViewModel(
    private val cloudRepository: CloudFitnessRepository,
    private val activityRepository: ActivityRepository,
    private val currentUserId: String?
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(WorkoutFilter.ALL)

    private val workoutsFlow: Flow<List<CloudWorkoutSession>> = if (currentUserId != null) {
        cloudRepository.observeCloudWorkouts(currentUserId)
    } else {
        activityRepository.observeAllWorkouts().map { list ->
            list.map { entity ->
                CloudWorkoutSession(
                    id = entity.id.toString(),
                    userId = "guest",
                    type = entity.type,
                    startedAtMillis = entity.startedAtMillis,
                    endedAtMillis = entity.endedAtMillis,
                    movingTimeMillis = entity.movingTimeMillis,
                    distanceMeters = entity.distanceMeters,
                    averagePaceSecPerKm = entity.averagePaceSecPerKm.toLong(),
                    caloriesBurned = entity.caloriesBurned.toLong()
                )
            }
        }
    }

    private val dailyStepsFlow: Flow<List<CloudDailySteps>> = if (currentUserId != null) {
        cloudRepository.observeCloudDailySteps(currentUserId)
    } else {
        activityRepository.observeRecentDailySteps(30).map { list ->
            list.map { entity ->
                CloudDailySteps(
                    id = entity.dateKey,
                    userId = "guest",
                    dateKey = entity.dateKey,
                    steps = entity.steps.toLong(),
                    goalSteps = entity.goalSteps.toLong()
                )
            }
        }
    }

    val uiState: StateFlow<UiState<HistoryData>> = combine(
        workoutsFlow,
        dailyStepsFlow,
        _selectedFilter
    ) { sessions, dailySteps, filter ->
        val filtered = when (filter) {
            WorkoutFilter.ALL -> sessions
            WorkoutFilter.RUN -> sessions.filter { WorkoutType.fromRaw(it.type) == WorkoutType.RUN }
            WorkoutFilter.WALK -> sessions.filter { WorkoutType.fromRaw(it.type) == WorkoutType.WALK }
        }
        HistoryData(
            sessions = sessions,
            filteredSessions = filtered,
            selectedFilter = filter,
            recentDailySteps = dailySteps.take(7),
            totalWorkoutsCount = sessions.size,
            totalDistanceMeters = sessions.sumOf { it.distanceMeters },
            totalMovingTimeMillis = sessions.sumOf { it.movingTimeMillis }
        )
    }
        .map<HistoryData, UiState<HistoryData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Failed to load workout history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    fun setFilter(filter: WorkoutFilter) {
        _selectedFilter.value = filter
    }

    fun observeSessionById(sessionId: String): Flow<CloudWorkoutSession?> {
        return if (currentUserId != null) {
            cloudRepository.observeCloudWorkoutById(currentUserId, sessionId)
                .catch { emit(null) }
        } else {
            val localId = sessionId.toLongOrNull() ?: return flowOf(null)
            activityRepository.observeWorkoutById(localId).map { entity ->
                entity?.let {
                    CloudWorkoutSession(
                        id = it.id.toString(),
                        userId = "guest",
                        type = it.type,
                        startedAtMillis = it.startedAtMillis,
                        endedAtMillis = it.endedAtMillis,
                        movingTimeMillis = it.movingTimeMillis,
                        distanceMeters = it.distanceMeters,
                        averagePaceSecPerKm = it.averagePaceSecPerKm.toLong(),
                        caloriesBurned = it.caloriesBurned.toLong()
                    )
                }
            }.catch { emit(null) }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (currentUserId != null) {
                cloudRepository.deleteCloudWorkout(currentUserId, sessionId)
            } else {
                sessionId.toLongOrNull()?.let { localId ->
                    activityRepository.deleteWorkoutSession(localId)
                }
            }
        }
    }

    class Factory(
        private val cloudRepository: CloudFitnessRepository,
        private val activityRepository: ActivityRepository,
        private val currentUserId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(cloudRepository, activityRepository, currentUserId) as T
        }
    }
}
