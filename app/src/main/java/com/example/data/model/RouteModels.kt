package com.example.data.model

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long
)

enum class WorkoutType(val label: String) {
    RUN("Run"),
    WALK("Walk");

    companion object {
        fun fromRaw(raw: String): WorkoutType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: RUN
    }
}
