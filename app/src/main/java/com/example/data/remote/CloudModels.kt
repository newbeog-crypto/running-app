package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.WorkoutSessionEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserPrivateProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val username: String? = null,
    val usernameLower: String? = null,
    val dailyStepGoal: Long = 8000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicRunnerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val leaderboardVisible: Boolean = true,
    val totalDistanceMeters: Double = 0.0,
    val weeklyDistanceMeters: Double = 0.0,
    val monthlyDistanceMeters: Double = 0.0,
    val yearlyDistanceMeters: Double = 0.0,
    val totalWorkouts: Long = 0L,
    val weeklyWorkouts: Long = 0L,
    val monthlyWorkouts: Long = 0L,
    val yearlyWorkouts: Long = 0L,
    val totalMovingTimeMillis: Long = 0L,
    val averagePaceSecPerKm: Long = 0L,
    val bestPaceSecPerKm: Long = 0L,
    val totalSteps: Long = 0L,
    val weekKey: String = "",
    val monthKey: String = "",
    val yearKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudWorkoutSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "RUN",
    val startedAtMillis: Long = 0L,
    val endedAtMillis: Long = 0L,
    val movingTimeMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averagePaceSecPerKm: Long = 0L,
    val caloriesBurned: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toLocalEntity(localId: Long = startedAtMillis): WorkoutSessionEntity =
        WorkoutSessionEntity(
            id = localId,
            type = type,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            movingTimeMillis = movingTimeMillis,
            distanceMeters = distanceMeters,
            averagePaceSecPerKm = averagePaceSecPerKm.toInt(),
            caloriesBurned = caloriesBurned.toInt()
        )
}

@Keep
@IgnoreExtraProperties
data class CloudDailySteps(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val steps: Long = 0L,
    val goalSteps: Long = 8000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequestDoc(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val toUid: String = "",
    val toUsername: String = "",
    val status: String = "PENDING",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
