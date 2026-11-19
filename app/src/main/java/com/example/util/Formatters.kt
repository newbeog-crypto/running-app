package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.data.model.RoutePoint
import com.example.data.model.WorkoutType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DateUtils {
    private val keyFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val shortDayFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEE", Locale.getDefault())

    private val fullDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private val dateTimeFormat: SimpleDateFormat
        get() = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    private val timeOnlyFormat: SimpleDateFormat
        get() = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val monthKeyFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM", Locale.US)

    private val yearKeyFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy", Locale.US)

    fun todayDateKey(): String = keyFormat.format(Date())

    fun dateKeyFor(millis: Long): String = keyFormat.format(Date(millis))

    fun weekKeyFor(millis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = millis
        }
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        return String.format(Locale.US, "%d-W%02d", year, week)
    }

    fun monthKeyFor(millis: Long = System.currentTimeMillis()): String =
        monthKeyFormat.format(Date(millis))

    fun yearKeyFor(millis: Long = System.currentTimeMillis()): String =
        yearKeyFormat.format(Date(millis))

    fun formatHeaderDate(millis: Long = System.currentTimeMillis()): String =
        fullDateFormat.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

    fun formatTimeOnly(millis: Long): String = timeOnlyFormat.format(Date(millis))

    fun formatShortDayFromKey(dateKey: String): String {
        val parsed = runCatching { keyFormat.parse(dateKey) }.getOrNull() ?: return dateKey
        return shortDayFormat.format(parsed)
    }

    fun formatRelativeDayFromKey(dateKey: String): String {
        val today = todayDateKey()
        if (dateKey == today) return "Today"
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = keyFormat.format(cal.time)
        if (dateKey == yesterday) return "Yesterday"
        val parsed = runCatching { keyFormat.parse(dateKey) }.getOrNull() ?: return dateKey
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(parsed)
    }

    fun lastSevenDateKeys(): List<String> {
        val cal = Calendar.getInstance()
        val keys = mutableListOf<String>()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            keys.add(keyFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return keys
    }
}

object MetricUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val AVG_STRIDE_METERS = 0.76
    private const val KCAL_PER_STEP = 0.04

    fun formatInteger(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    fun formatLong(value: Long): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    fun estimateDistanceKmFromSteps(steps: Int): Double =
        (steps * AVG_STRIDE_METERS) / 1000.0

    fun estimateCaloriesFromSteps(steps: Int): Int =
        (steps * KCAL_PER_STEP).roundToInt()

    fun formatDistanceKm(distanceMeters: Double): String {
        val km = distanceMeters / 1000.0
        return String.format(Locale.US, "%.2f", km)
    }

    fun formatSpeedKmH(distanceMeters: Double, durationMillis: Long): String {
        if (distanceMeters < 10.0 || durationMillis <= 0L) return "0.0"
        val km = distanceMeters / 1000.0
        val hours = durationMillis / 3_600_000.0
        val kmh = (km / hours).coerceIn(0.0, 60.0)
        return String.format(Locale.US, "%.1f", kmh)
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun calculateAveragePaceSecPerKm(distanceMeters: Double, durationMillis: Long): Int {
        if (distanceMeters < 10.0 || durationMillis <= 0L) return 0
        val km = distanceMeters / 1000.0
        val seconds = durationMillis / 1000.0
        return (seconds / km).roundToInt().coerceIn(0, 3599)
    }

    fun formatPace(paceSecPerKm: Int): String {
        if (paceSecPerKm <= 0) return "--'--\""
        val mins = paceSecPerKm / 60
        val secs = paceSecPerKm % 60
        return String.format(Locale.US, "%d'%02d\"", mins, secs)
    }

    fun calculateWorkoutCalories(
        distanceMeters: Double,
        durationMillis: Long,
        type: WorkoutType
    ): Int {
        val km = distanceMeters / 1000.0
        val distanceBased = when (type) {
            WorkoutType.RUN -> km * 64.0
            WorkoutType.WALK -> km * 48.0
        }
        val minTimeBased = (durationMillis / 60_000.0) * if (type == WorkoutType.RUN) 8.0 else 4.2
        return maxOf(distanceBased, minTimeBased).roundToInt().coerceAtLeast(0)
    }

    fun haversineMeters(a: RoutePoint, b: RoutePoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinDLat = sin(dLat / 2.0)
        val sinDLon = sin(dLon / 2.0)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1.0 - h))
    }

    fun normalizeUsernameKey(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    fun isValidUsernameFormat(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.length !in 2..24) return false
        if (trimmed == "." || trimmed == "..") return false
        return trimmed.none { it == '/' || it.isWhitespace() }
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
