package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.strideDataStore: DataStore<Preferences> by preferencesDataStore(name = "stride_pulse_prefs")

data class SensorBaseline(
    val dateKey: String,
    val baselineRawSteps: Float
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DAILY_STEP_GOAL = intPreferencesKey("daily_step_goal")
        val BASELINE_DATE = stringPreferencesKey("sensor_baseline_date")
        val BASELINE_RAW_STEPS = floatPreferencesKey("sensor_baseline_raw_steps")
        val ACTIVITY_PERM_ASKED = booleanPreferencesKey("activity_perm_asked")
        val LOCATION_PERM_ASKED = booleanPreferencesKey("location_perm_asked")
        val BACKGROUND_STEPS_ENABLED = booleanPreferencesKey("background_steps_enabled")
        val IS_GUEST_MODE = booleanPreferencesKey("is_guest_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    val dailyStepGoal: Flow<Int> = context.strideDataStore.data.map { prefs ->
        prefs[Keys.DAILY_STEP_GOAL] ?: DEFAULT_STEP_GOAL
    }

    val wasActivityPermissionAsked: Flow<Boolean> = context.strideDataStore.data.map { prefs ->
        prefs[Keys.ACTIVITY_PERM_ASKED] ?: false
    }

    val wasLocationPermissionAsked: Flow<Boolean> = context.strideDataStore.data.map { prefs ->
        prefs[Keys.LOCATION_PERM_ASKED] ?: false
    }

    val backgroundStepsEnabled: Flow<Boolean> = context.strideDataStore.data.map { prefs ->
        prefs[Keys.BACKGROUND_STEPS_ENABLED] ?: true
    }

    val isGuestMode: Flow<Boolean> = context.strideDataStore.data.map { prefs ->
        prefs[Keys.IS_GUEST_MODE] ?: false
    }

    val appLanguageCode: Flow<String?> = context.strideDataStore.data.map { prefs ->
        prefs[Keys.APP_LANGUAGE]
    }

    suspend fun setAppLanguageCode(code: String) {
        context.strideDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = code
        }
    }

    suspend fun getDailyStepGoalOnce(): Int = dailyStepGoal.first()

    suspend fun setDailyStepGoal(goal: Int) {
        val clamped = goal.coerceIn(MIN_STEP_GOAL, MAX_STEP_GOAL)
        context.strideDataStore.edit { prefs ->
            prefs[Keys.DAILY_STEP_GOAL] = clamped
        }
    }

    suspend fun getSensorBaselineOnce(): SensorBaseline? {
        val prefs = context.strideDataStore.data.first()
        val date = prefs[Keys.BASELINE_DATE] ?: return null
        val raw = prefs[Keys.BASELINE_RAW_STEPS] ?: return null
        return SensorBaseline(dateKey = date, baselineRawSteps = raw)
    }

    suspend fun saveSensorBaseline(dateKey: String, baselineRawSteps: Float) {
        context.strideDataStore.edit { prefs ->
            prefs[Keys.BASELINE_DATE] = dateKey
            prefs[Keys.BASELINE_RAW_STEPS] = baselineRawSteps
        }
    }

    suspend fun markActivityPermissionAsked() {
        context.strideDataStore.edit { prefs ->
            prefs[Keys.ACTIVITY_PERM_ASKED] = true
        }
    }

    suspend fun markLocationPermissionAsked() {
        context.strideDataStore.edit { prefs ->
            prefs[Keys.LOCATION_PERM_ASKED] = true
        }
    }

    suspend fun setBackgroundStepsEnabled(enabled: Boolean) {
        context.strideDataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_STEPS_ENABLED] = enabled
        }
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.strideDataStore.edit { prefs ->
            prefs[Keys.IS_GUEST_MODE] = enabled
        }
    }

    companion object {
        const val DEFAULT_STEP_GOAL = 8_000
        const val MIN_STEP_GOAL = 1_000
        const val MAX_STEP_GOAL = 50_000
    }
}
