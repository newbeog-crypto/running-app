package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val movingTimeMillis: Long,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Int,
    val caloriesBurned: Int
)
