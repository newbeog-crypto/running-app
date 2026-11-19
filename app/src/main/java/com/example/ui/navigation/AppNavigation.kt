package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.i18n.AppStrings

object AppRoutes {
    const val TODAY = "today"
    const val RUN = "run"
    const val STATS = "stats"
    const val HISTORY = "history"
    const val SOCIAL = "social"
    const val WORKOUT_DETAIL_PATTERN = "workout_detail/{sessionId}"

    fun workoutDetail(sessionId: String): String = "workout_detail/$sessionId"
}

enum class BottomNavTab(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TODAY(
        route = AppRoutes.TODAY,
        selectedIcon = Icons.Filled.Today,
        unselectedIcon = Icons.Outlined.Today,
        testTag = "nav_tab_today"
    ),
    RUN(
        route = AppRoutes.RUN,
        selectedIcon = Icons.AutoMirrored.Filled.DirectionsRun,
        unselectedIcon = Icons.AutoMirrored.Outlined.DirectionsRun,
        testTag = "nav_tab_run"
    ),
    STATS(
        route = AppRoutes.STATS,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_tab_stats"
    ),
    HISTORY(
        route = AppRoutes.HISTORY,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    ),
    SOCIAL(
        route = AppRoutes.SOCIAL,
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        testTag = "nav_tab_social"
    );

    fun label(strings: AppStrings): String = when (this) {
        TODAY -> strings.tabToday
        RUN -> strings.tabRun
        STATS -> strings.tabStats
        HISTORY -> strings.tabHistory
        SOCIAL -> strings.tabSocial
    }
}
