package com.example.ui.run

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.WorkoutType
import com.example.sensors.GpsSignalState
import com.example.ui.components.MetricTileCard
import com.example.ui.components.PermissionStatusBanner
import com.example.ui.i18n.LocalStrings
import com.example.ui.today.findActivity
import com.example.util.MetricUtils
import com.example.util.openAppSettings

@Composable
fun RunScreen(
    viewModel: RunViewModel,
    onWorkoutSaved: (String) -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingStartAfterPermission by rememberSaveable { mutableStateOf(false) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = hasLocationPermission() ||
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionStateChanged(granted)
        if (pendingStartAfterPermission && granted) {
            pendingStartAfterPermission = false
            viewModel.startSession()
        } else {
            pendingStartAfterPermission = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.onLocationPermissionStateChanged(hasLocationPermission())
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onLocationPermissionStateChanged(hasLocationPermission())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.lastSavedSessionId, uiState.saveErrorMessage) {
        val savedId = uiState.lastSavedSessionId
        val errMsg = uiState.saveErrorMessage
        if (savedId != null) {
            viewModel.consumeSavedEvent()
            onWorkoutSaved(savedId)
        } else if (errMsg != null) {
            viewModel.consumeSavedEvent()
            onShowMessage(errMsg)
        }
    }

    val activity = context.findActivity()
    val needsLocationPermission = !uiState.locationPermissionGranted
    val isPermanentlyDenied = needsLocationPermission &&
        uiState.wasLocationPermissionAsked &&
        activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

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
            // Top Header & GPS Status
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val workoutLabel = if (uiState.workoutType == WorkoutType.RUN) strings.runLabel else strings.walkLabel
                        Text(
                            text = when (uiState.sessionState) {
                                SessionState.IDLE -> strings.readyToMove
                                SessionState.RUNNING -> strings.trackingLive
                                SessionState.PAUSED -> strings.sessionPaused
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "$workoutLabel ${strings.trackerSuffix}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    GpsStatusBadge(signalState = uiState.gpsSignalState)
                }
            }

            // Location Permission Status Banner
            if (needsLocationPermission) {
                item {
                    PermissionStatusBanner(
                        title = if (isPermanentlyDenied) {
                            strings.locationPermBlockedTitle
                        } else {
                            strings.locationPermNeededTitle
                        },
                        description = if (isPermanentlyDenied) {
                            strings.locationPermBlockedDesc
                        } else {
                            strings.locationPermNeededDesc
                        },
                        isPermanentlyDenied = isPermanentlyDenied,
                        onRequestPermission = {
                            viewModel.markLocationPermissionAsked()
                            val perms = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        onOpenSettings = { openAppSettings(context) },
                        actionTestTag = "location_permission_button"
                    )
                }
            }

            // Activity Type Selector (Run vs Walk)
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WorkoutType.entries.forEachIndexed { index, type ->
                        val typeLabel = if (type == WorkoutType.RUN) strings.runLabel else strings.walkLabel
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = WorkoutType.entries.size
                            ),
                            selected = uiState.workoutType == type,
                            onClick = { viewModel.selectWorkoutType(type) },
                            enabled = uiState.sessionState == SessionState.IDLE,
                            icon = {
                                Icon(
                                    imageVector = if (type == WorkoutType.RUN) {
                                        Icons.AutoMirrored.Filled.DirectionsRun
                                    } else {
                                        Icons.AutoMirrored.Filled.DirectionsWalk
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("workout_type_${type.name.lowercase()}")
                        ) {
                            Text(typeLabel)
                        }
                    }
                }
            }

            // High-Contrast Live Telemetry Hero Card (Distance, Duration & Background Active Banner)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.sessionState == SessionState.RUNNING) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.workoutType == WorkoutType.RUN) {
                                        Icons.AutoMirrored.Filled.DirectionsRun
                                    } else {
                                        Icons.AutoMirrored.Filled.DirectionsWalk
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (uiState.workoutType == WorkoutType.RUN) strings.runLabel else strings.walkLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (uiState.sessionState != SessionState.IDLE) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = strings.backgroundActiveBadge,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = strings.backgroundActiveBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = strings.distanceUpper,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = MetricUtils.formatDistanceKm(uiState.distanceMeters),
                                        style = MaterialTheme.typography.displayLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "km",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 7.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = strings.elapsedTimeUpper,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MetricUtils.formatDuration(uiState.elapsedMillis),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = strings.backgroundWorkoutHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2x2 Outdoor Pace, Speed & Calories Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTileCard(
                            label = strings.currentPace,
                            value = MetricUtils.formatPace(uiState.currentPaceSecPerKm),
                            unit = "/km",
                            icon = Icons.Default.Speed,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTileCard(
                            label = strings.avgPace,
                            value = MetricUtils.formatPace(uiState.averagePaceSecPerKm),
                            unit = "/km",
                            icon = Icons.Default.Timer,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTileCard(
                            label = strings.avgSpeedLabel,
                            value = MetricUtils.formatSpeedKmH(uiState.distanceMeters, uiState.elapsedMillis),
                            unit = "km/h",
                            icon = Icons.Default.Directions,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTileCard(
                            label = strings.burnedLabel,
                            value = MetricUtils.formatInteger(uiState.caloriesBurned),
                            unit = "kcal",
                            icon = Icons.Default.LocalFireDepartment,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Large Outdoor-Friendly Session Controls
            item {
                val activeTypeLabel = if (uiState.workoutType == WorkoutType.RUN) strings.runLabel else strings.walkLabel
                when (uiState.sessionState) {
                    SessionState.IDLE -> {
                        Button(
                            onClick = {
                                if (!uiState.locationPermissionGranted) {
                                    viewModel.markLocationPermissionAsked()
                                    pendingStartAfterPermission = true
                                    val perms = buildList {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    permissionLauncher.launch(perms.toTypedArray())
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    }
                                    viewModel.startSession()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .testTag("start_workout_button"),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!uiState.locationPermissionGranted) {
                                    "${strings.allowGpsAndStart} ($activeTypeLabel)"
                                } else {
                                    "${strings.startAction} $activeTypeLabel"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    SessionState.RUNNING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.pauseSession() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .testTag("pause_workout_button"),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.pauseAction,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Button(
                                onClick = { viewModel.finishAndSaveSession() },
                                enabled = !uiState.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .testTag("stop_save_workout_button"),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.finishSaveAction,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    SessionState.PAUSED -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resumeSession() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .testTag("resume_workout_button"),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.resumeAction,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Button(
                                    onClick = { viewModel.finishAndSaveSession() },
                                    enabled = !uiState.isSaving,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .testTag("stop_save_workout_button"),
                                    shape = MaterialTheme.shapes.large,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.finishSaveAction,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showDiscardDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("discard_workout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.discardSession)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(strings.discardTitle) },
            text = { Text(strings.discardDesc) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(strings.discardConfirm)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(strings.keepTracking)
                }
            }
        )
    }
}

@Composable
private fun GpsStatusBadge(signalState: GpsSignalState) {
    val strings = LocalStrings.current
    val (label, icon, container, content) = when (signalState) {
        GpsSignalState.ACTIVE_FIX -> Quadruple(
            strings.gpsActive,
            Icons.Default.GpsFixed,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        GpsSignalState.SEARCHING -> Quadruple(
            strings.gpsSearching,
            Icons.Default.GpsNotFixed,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        GpsSignalState.PERMISSION_REQUIRED -> Quadruple(
            strings.gpsNeeded,
            Icons.Default.GpsOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        GpsSignalState.IDLE -> Quadruple(
            strings.gpsReady,
            Icons.Default.GpsFixed,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
