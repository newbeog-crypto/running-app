package com.example.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.WorkoutType
import com.example.data.remote.CloudDailySteps
import com.example.data.remote.CloudWorkoutSession
import com.example.data.remote.UiState
import com.example.ui.components.EmptyHistoryTrailArtwork
import com.example.ui.components.MetricTileCard
import com.example.ui.i18n.LocalStrings
import com.example.util.DateUtils
import com.example.util.MetricUtils

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onSessionClick: (String) -> Unit,
    onStartRunClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteSession by remember { mutableStateOf<CloudWorkoutSession?>(null) }

    when (val ui = state) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(strings.loadingLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ui.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is UiState.Success -> {
            val uiState = ui.data
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = strings.activityLogTag,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = strings.historyTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Aggregate Totals
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTileCard(
                                label = strings.workoutsLabel,
                                value = uiState.totalWorkoutsCount.toString(),
                                unit = strings.savedUnit,
                                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricTileCard(
                                label = strings.distanceLabel,
                                value = MetricUtils.formatDistanceKm(uiState.totalDistanceMeters),
                                unit = "km",
                                icon = Icons.Default.Route,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricTileCard(
                                label = strings.movingTimeLabel,
                                value = MetricUtils.formatDuration(uiState.totalMovingTimeMillis),
                                unit = "",
                                icon = Icons.Default.Timer,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Recent Daily Step Totals Strip
                    if (uiState.recentDailySteps.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = strings.dailyStepTotals,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = uiState.recentDailySteps,
                                        key = { it.dateKey }
                                    ) { daily ->
                                        DailyStepSummaryChip(daily = daily)
                                    }
                                }
                            }
                        }
                    }

                    // Filter Chips
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WorkoutFilter.entries.forEach { filter ->
                                val filterLabel = when (filter) {
                                    WorkoutFilter.ALL -> strings.filterAll
                                    WorkoutFilter.RUN -> strings.filterRuns
                                    WorkoutFilter.WALK -> strings.filterWalks
                                }
                                FilterChip(
                                    selected = uiState.selectedFilter == filter,
                                    onClick = { viewModel.setFilter(filter) },
                                    label = { Text(filterLabel) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("history_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Empty State or Saved Sessions List
                    if (uiState.filteredSessions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    EmptyHistoryTrailArtwork(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                    )
                                    Text(
                                        text = strings.noSavedRoutesTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = strings.noSavedRoutesDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = onStartRunClick,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("empty_history_start_run_button")
                                    ) {
                                        Text(strings.startFirstWorkout)
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = uiState.filteredSessions,
                            key = { it.id }
                        ) { session ->
                            WorkoutHistoryCard(
                                session = session,
                                onClick = { onSessionClick(session.id) },
                                onDeleteClick = { pendingDeleteSession = session }
                            )
                        }
                    }
                }
            }

            pendingDeleteSession?.let { target ->
                val targetTypeLabel = if (WorkoutType.fromRaw(target.type) == WorkoutType.RUN) {
                    strings.runLabel
                } else {
                    strings.walkLabel
                }
                AlertDialog(
                    onDismissRequest = { pendingDeleteSession = null },
                    title = { Text(strings.deleteWorkoutTitle) },
                    text = {
                        Text(
                            "$targetTypeLabel (${MetricUtils.formatDistanceKm(target.distanceMeters)} km) • ${strings.deletePermanentDesc}"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteSession(target.id)
                                pendingDeleteSession = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("confirm_delete_session_button")
                        ) {
                            Text(strings.deleteConfirm)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { pendingDeleteSession = null },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(strings.cancel)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyStepSummaryChip(daily: CloudDailySteps) {
    val strings = LocalStrings.current
    val hitGoal = daily.steps >= daily.goalSteps
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (hitGoal) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = DateUtils.formatRelativeDayFromKey(daily.dateKey),
                style = MaterialTheme.typography.labelMedium,
                color = if (hitGoal) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "${MetricUtils.formatLong(daily.steps)} ${strings.stepsUnit}",
                style = MaterialTheme.typography.titleSmall,
                color = if (hitGoal) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WorkoutHistoryCard(
    session: CloudWorkoutSession,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val strings = LocalStrings.current
    val workoutType = WorkoutType.fromRaw(session.type)
    val typeLabel = if (workoutType == WorkoutType.RUN) strings.runLabel else strings.walkLabel

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("workout_card_${session.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (workoutType == WorkoutType.RUN) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (workoutType == WorkoutType.RUN) {
                            Icons.AutoMirrored.Filled.DirectionsRun
                        } else {
                            Icons.AutoMirrored.Filled.DirectionsWalk
                        },
                        contentDescription = typeLabel,
                        tint = if (workoutType == WorkoutType.RUN) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (workoutType == WorkoutType.RUN) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (workoutType == WorkoutType.RUN) {
                                    Icons.AutoMirrored.Filled.DirectionsRun
                                } else {
                                    Icons.AutoMirrored.Filled.DirectionsWalk
                                },
                                contentDescription = typeLabel,
                                tint = if (workoutType == WorkoutType.RUN) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (workoutType == WorkoutType.RUN) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }

                    Text(
                        text = DateUtils.formatDateTime(session.startedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${MetricUtils.formatDistanceKm(session.distanceMeters)} km",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.distanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(
                            text = MetricUtils.formatDuration(session.movingTimeMillis),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.movingTimeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(
                            text = MetricUtils.formatPace(session.averagePaceSecPerKm.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.avgPace,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_workout_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = strings.deleteConfirm,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = strings.sessionDetails,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
