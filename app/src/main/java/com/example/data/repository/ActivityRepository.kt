package com.example.data.repository

import com.example.data.local.DailyStepsDao
import com.example.data.local.DailyStepsEntity
import com.example.data.local.WorkoutSessionDao
import com.example.data.local.WorkoutSessionEntity
import com.example.data.model.WorkoutType
import com.example.data.preferences.UserPreferencesRepository
import com.example.util.DateUtils
import com.example.util.MetricUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class ActivityRepository(
    private val dailyStepsDao: DailyStepsDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val preferences: UserPreferencesRepository
) {
    val dailyStepGoal: Flow<Int> = preferences.dailyStepGoal
    val wasActivityPermissionAsked: Flow<Boolean> = preferences.wasActivityPermissionAsked
    val wasLocationPermissionAsked: Flow<Boolean> = preferences.wasLocationPermissionAsked
    val backgroundStepsEnabled: Flow<Boolean> = preferences.backgroundStepsEnabled

    fun observeTodaySteps(): Flow<DailyStepsEntity> {
        val todayKey = DateUtils.todayDateKey()
        return dailyStepsDao.observeByDate(todayKey).map { entity ->
            entity ?: DailyStepsEntity(
                dateKey = todayKey,
                steps = 0,
                goalSteps = preferences.getDailyStepGoalOnce(),
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun observeRecentDailySteps(limit: Int = 7): Flow<List<DailyStepsEntity>> =
        dailyStepsDao.observeRecentDays(limit)

    fun observeAllWorkouts(): Flow<List<WorkoutSessionEntity>> =
        workoutSessionDao.observeAllSessions()

    fun observeWorkoutById(id: Long): Flow<WorkoutSessionEntity?> =
        workoutSessionDao.observeSessionById(id)

    suspend fun updateDailyGoal(newGoal: Int) {
        val clamped = newGoal.coerceIn(
            UserPreferencesRepository.MIN_STEP_GOAL,
            UserPreferencesRepository.MAX_STEP_GOAL
        )
        preferences.setDailyStepGoal(clamped)
        val todayKey = DateUtils.todayDateKey()
        val existing = dailyStepsDao.getByDateOnce(todayKey)
        dailyStepsDao.upsert(
            DailyStepsEntity(
                dateKey = todayKey,
                steps = existing?.steps ?: 0,
                goalSteps = clamped,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordHardwareSensorReading(totalStepsSinceBoot: Float) {
        val todayKey = DateUtils.todayDateKey()
        val goal = preferences.getDailyStepGoalOnce()
        val existingToday = dailyStepsDao.getByDateOnce(todayKey)
        val persistedTodaySteps = existingToday?.steps ?: 0
        val baseline = preferences.getSensorBaselineOnce()

        val computedTodaySteps = when {
            baseline == null || baseline.dateKey != todayKey -> {
                // First reading of the day (or first launch): preserve any already-logged steps for today
                val adjustedBaseline = (totalStepsSinceBoot - persistedTodaySteps).coerceAtLeast(0f)
                preferences.saveSensorBaseline(todayKey, adjustedBaseline)
                persistedTodaySteps
            }
            totalStepsSinceBoot < baseline.baselineRawSteps -> {
                // Device rebooted since baseline was recorded: keep persisted steps and rebase
                val adjustedBaseline = (totalStepsSinceBoot - persistedTodaySteps).coerceAtLeast(0f)
                preferences.saveSensorBaseline(todayKey, adjustedBaseline)
                persistedTodaySteps
            }
            else -> {
                val delta = (totalStepsSinceBoot - baseline.baselineRawSteps).roundToInt().coerceAtLeast(0)
                maxOf(delta, persistedTodaySteps)
            }
        }

        if (existingToday == null || computedTodaySteps != existingToday.steps || existingToday.goalSteps != goal) {
            dailyStepsDao.upsert(
                DailyStepsEntity(
                    dateKey = todayKey,
                    steps = computedTodaySteps,
                    goalSteps = goal,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveWorkoutSession(
        type: WorkoutType,
        startedAtMillis: Long,
        endedAtMillis: Long,
        movingTimeMillis: Long,
        distanceMeters: Double
    ): Long {
        val avgPaceSecPerKm = MetricUtils.calculateAveragePaceSecPerKm(
            distanceMeters = distanceMeters,
            durationMillis = movingTimeMillis
        )
        val calories = MetricUtils.calculateWorkoutCalories(
            distanceMeters = distanceMeters,
            durationMillis = movingTimeMillis,
            type = type
        )
        val entity = WorkoutSessionEntity(
            type = type.name,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            movingTimeMillis = movingTimeMillis,
            distanceMeters = distanceMeters,
            averagePaceSecPerKm = avgPaceSecPerKm,
            caloriesBurned = calories
        )
        return workoutSessionDao.insertSession(entity)
    }

    suspend fun deleteWorkoutSession(id: Long) {
        workoutSessionDao.deleteSessionById(id)
    }

    suspend fun markActivityPermissionAsked() {
        preferences.markActivityPermissionAsked()
    }

    suspend fun markLocationPermissionAsked() {
        preferences.markLocationPermissionAsked()
    }

    suspend fun setBackgroundStepsEnabled(enabled: Boolean) {
        preferences.setBackgroundStepsEnabled(enabled)
    }

    suspend fun ensureTodayRowExists() {
        val todayKey = DateUtils.todayDateKey()
        if (dailyStepsDao.getByDateOnce(todayKey) == null) {
            val goal = dailyStepGoal.first()
            dailyStepsDao.upsert(
                DailyStepsEntity(
                    dateKey = todayKey,
                    steps = 0,
                    goalSteps = goal,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }
}
