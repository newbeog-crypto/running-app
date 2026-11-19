package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.WorkoutType
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ActivityRepository
import com.example.util.MetricUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches StridePulse`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StridePulse", appName)
    }

    @Test
    fun `metric utils calculates pace and distance accurately`() {
        val pace = MetricUtils.calculateAveragePaceSecPerKm(
            distanceMeters = 2000.0,
            durationMillis = 600_000L
        )
        assertEquals(300, pace)
        assertEquals("5'00\"", MetricUtils.formatPace(pace))
        assertEquals("2.00", MetricUtils.formatDistanceKm(2000.0))
    }

    @Test
    fun `repository persists hardware steps background toggle and workout session without routes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = UserPreferencesRepository(context)
            val repo = ActivityRepository(
                dailyStepsDao = db.dailyStepsDao(),
                workoutSessionDao = db.workoutSessionDao(),
                preferences = prefs
            )

            repo.updateDailyGoal(10_000)
            assertTrue(repo.backgroundStepsEnabled.first())
            assertTrue(!prefs.isGuestMode.first())
            prefs.setGuestMode(true)
            assertTrue(prefs.isGuestMode.first())

            // Hardware step baseline then +1250 steps
            repo.recordHardwareSensorReading(5000f)
            repo.recordHardwareSensorReading(6250f)
            val today = repo.observeTodaySteps().first()
            assertEquals(1250, today.steps)
            assertEquals(10_000, today.goalSteps)

            val savedId = repo.saveWorkoutSession(
                type = WorkoutType.RUN,
                startedAtMillis = 1_000L,
                endedAtMillis = 121_000L,
                movingTimeMillis = 120_000L,
                distanceMeters = 500.0
            )
            val fetched = repo.observeWorkoutById(savedId).first()
            assertNotNull(fetched)
            assertEquals(500.0, fetched?.distanceMeters ?: 0.0, 0.01)
            assertTrue((fetched?.caloriesBurned ?: 0) > 0)
        } finally {
            db.close()
        }
    }

    @Test
    fun `language switcher supports Korean English and Chinese`() {
        val ko = com.example.ui.i18n.stringsFor(com.example.ui.i18n.AppLanguage.KO)
        val en = com.example.ui.i18n.stringsFor(com.example.ui.i18n.AppLanguage.EN)
        val zh = com.example.ui.i18n.stringsFor(com.example.ui.i18n.AppLanguage.ZH)
        assertEquals("오늘", ko.tabToday)
        assertEquals("Today", en.tabToday)
        assertEquals("今日", zh.tabToday)
    }

    @Test
    fun `username validation and speed calculation work accurately`() {
        assertTrue(MetricUtils.isValidUsernameFormat("런닝왕"))
        assertTrue(MetricUtils.isValidUsernameFormat("Runner_01"))
        assertTrue(!MetricUtils.isValidUsernameFormat("a"))
        assertTrue(!MetricUtils.isValidUsernameFormat("has space"))
        assertEquals("12.0", MetricUtils.formatSpeedKmH(6000.0, 1_800_000L))
    }
}
