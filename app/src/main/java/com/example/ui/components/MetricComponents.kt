package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.i18n.LocalStrings
import com.example.ui.today.DailyStepBar
import com.example.util.MetricUtils
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PermissionStatusBanner(
    title: String,
    description: String,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actionTestTag: String = "permission_action_button"
) {
    val strings = LocalStrings.current
    val containerColor = if (isPermanentlyDenied) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isPermanentlyDenied) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isPermanentlyDenied) Icons.Outlined.Lock else Icons.Outlined.Info,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.9f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isPermanentlyDenied) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(actionTestTag),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = containerColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.openSettings)
                    }
                } else {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(actionTestTag),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor,
                            contentColor = containerColor
                        )
                    ) {
                        Text(strings.grantPermission)
                    }
                }
            }
        }
    }
}

@Composable
fun StepProgressRing(
    steps: Int,
    goalSteps: Int,
    completionFraction: Float,
    completionPercent: Int,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = completionFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "step_ring_progress"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = modifier.size(228.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 18.dp.toPx()
            val inset = strokePx / 2f + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)
            val tickOuterR = size.minDimension / 2f - 2.dp.toPx()
            val tickInnerR = tickOuterR - 5.dp.toPx()

            // Subtle radial dial ticks around perimeter
            for (i in 0 until 24) {
                val angleRad = Math.toRadians((i * 15.0) - 90.0)
                val start = Offset(
                    center.x + (tickInnerR * cos(angleRad)).toFloat(),
                    center.y + (tickInnerR * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    center.x + (tickOuterR * cos(angleRad)).toFloat(),
                    center.y + (tickOuterR * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = tickColor,
                    start = start,
                    end = end,
                    strokeWidth = if (i % 6 == 0) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Track arc
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active sweep arc
            if (animatedFraction > 0.002f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(secondary, primary, primary),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        val strings = LocalStrings.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = strings.ofGoalFormat(completionPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = MetricUtils.formatInteger(steps),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = strings.ofStepsGoalFormat(MetricUtils.formatInteger(goalSteps)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricTileCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyStepsChart(
    days: List<DailyStepBar>,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val trackBar = MaterialTheme.colorScheme.surfaceContainerHighest
    val goalLineColor = MaterialTheme.colorScheme.outline

    val maxVal = maxOf(
        days.maxOfOrNull { maxOf(it.steps, it.goalSteps) } ?: 8000,
        1000
    ).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
        ) {
            val count = days.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.44f).coerceAtMost(28.dp.toPx())

            // Dashed goal line based on today's goal
            val goalRatio = (days.last().goalSteps.toFloat() / maxVal).coerceIn(0f, 1f)
            val goalY = size.height * (1f - goalRatio)
            drawLine(
                color = goalLineColor.copy(alpha = 0.65f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            days.forEachIndexed { index, bar ->
                val centerX = slotWidth * index + slotWidth / 2f
                val left = centerX - barWidth / 2f
                val ratio = (bar.steps.toFloat() / maxVal).coerceIn(0f, 1f)
                val barHeight = (size.height * ratio).coerceAtLeast(6.dp.toPx())

                // Background column slot
                drawRoundRect(
                    color = trackBar.copy(alpha = 0.45f),
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // Active steps bar
                val barColor = when {
                    bar.steps >= bar.goalSteps -> primary
                    bar.isToday -> secondary
                    else -> secondary.copy(alpha = 0.65f)
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            days.forEach { bar ->
                Text(
                    text = bar.dayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bar.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditorDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onSaveGoal: (Int) -> Unit
) {
    val strings = LocalStrings.current
    val presets = listOf(6_000, 8_000, 10_000)
    var inputText by remember(currentGoal) { mutableStateOf(currentGoal.toString()) }
    val parsed = inputText.toIntOrNull()
    val isValid = parsed != null &&
        parsed in UserPreferencesRepository.MIN_STEP_GOAL..UserPreferencesRepository.MAX_STEP_GOAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.dailyStepGoalTitle,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = strings.goalDialogDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val selected = parsed == preset
                        FilterChip(
                            selected = selected,
                            onClick = { inputText = preset.toString() },
                            label = {
                                Text(
                                    text = "${MetricUtils.formatInteger(preset)} ${strings.stepsUnit}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("goal_preset_$preset")
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { raw ->
                        val digitsOnly = raw.filter { it.isDigit() }.take(5)
                        inputText = digitsOnly
                    },
                    label = { Text(strings.customDailySteps) },
                    suffix = { Text(strings.stepsUnit) },
                    singleLine = true,
                    isError = inputText.isNotEmpty() && !isValid,
                    supportingText = if (inputText.isNotEmpty() && !isValid) {
                        { Text(strings.goalValidationError) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_goal_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onSaveGoal(parsed)
                    }
                },
                enabled = isValid,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_goal_button")
            ) {
                Text(strings.saveGoal)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_goal_button")
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun EmptyHistoryTrailArtwork(
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = outline,
            radius = h * 0.42f,
            center = Offset(w * 0.5f, h * 0.52f),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = outline,
            radius = h * 0.28f,
            center = Offset(w * 0.5f, h * 0.52f),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
        )

        val path = Path().apply {
            moveTo(w * 0.14f, h * 0.78f)
            cubicTo(
                w * 0.34f, h * 0.78f,
                w * 0.28f, h * 0.28f,
                w * 0.50f, h * 0.42f
            )
            cubicTo(
                w * 0.70f, h * 0.55f,
                w * 0.66f, h * 0.20f,
                w * 0.86f, h * 0.26f
            )
        }

        drawPath(
            path = path,
            color = primary.copy(alpha = 0.22f),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(secondary, primary)),
            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
        )

        drawCircle(color = secondary, radius = 6.dp.toPx(), center = Offset(w * 0.14f, h * 0.78f))
        drawCircle(color = primary, radius = 7.dp.toPx(), center = Offset(w * 0.86f, h * 0.26f))
    }
}
