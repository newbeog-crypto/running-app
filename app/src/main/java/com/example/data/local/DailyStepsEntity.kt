package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey val dateKey: String,
    val steps: Int,
    val goalSteps: Int,
    val updatedAtMillis: Long
)
