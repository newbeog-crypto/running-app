package com.example.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.StridePulseApplication
import com.example.data.model.WorkoutType
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import com.example.ui.run.SessionState
import com.example.util.MetricUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TrackingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false

    private val controller: TrackingSessionController
        get() = (application as StridePulseApplication).appContainer.trackingController

    private val appContainer
        get() = (application as StridePulseApplication).appContainer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> controller.pauseWorkout()
            ACTION_RESUME -> controller.resumeWorkout()
            ACTION_FINISH -> controller.finishAndSaveWorkout()
            ACTION_STOP -> {
                stopForegroundTracking()
                return START_NOT_STICKY
            }
        }

        if (!controller.shouldRunForegroundService()) {
            stopForegroundTracking()
            return START_NOT_STICKY
        }

        promoteToForeground()
        startObservingState()
        return START_STICKY
    }

    private fun startObservingState() {
        if (observerJob?.isActive == true) return
        observerJob = serviceScope.launch {
            combine(
                controller.workoutState,
                appContainer.activityRepository.observeTodaySteps(),
                controller.backgroundStepsActive,
                appContainer.userPreferences.appLanguageCode
            ) { workout, todaySteps, bgSteps, langCode ->
                ServiceRenderState(
                    workout = workout,
                    todaySteps = todaySteps.steps,
                    dailyGoal = todaySteps.goalSteps,
                    bgSteps = bgSteps,
                    language = AppLanguage.fromCode(langCode)
                )
            }.collect { render ->
                if (!controller.shouldRunForegroundService()) {
                    stopForegroundTracking()
                } else {
                    updateWakeLock(controller.needsWakeLock())
                    promoteToForeground(render)
                }
            }
        }
    }

    private fun promoteToForeground(renderState: ServiceRenderState? = null) {
        val notification = buildNotification(renderState)
        val serviceType = resolveForegroundServiceType()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                serviceType
            )
            isForegroundStarted = true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Foreground service start not allowed from background", e)
            } else {
                Log.e(TAG, "Failed to start foreground tracking service", e)
            }
            stopSelf()
        }
    }

    private fun resolveForegroundServiceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0

        var type = 0
        val workoutActive = controller.workoutState.value.sessionState != SessionState.IDLE
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (workoutActive && hasLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }

        val hasActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (controller.backgroundStepsActive.value &&
            hasActivity &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }

        if (type == 0) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return type
    }

    private fun buildNotification(renderState: ServiceRenderState?): Notification {
        val workout = renderState?.workout ?: controller.workoutState.value
        val language = renderState?.language ?: AppLanguage.EN
        val strings = stringsFor(language)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (workout.sessionState != SessionState.IDLE) {
            val typeLabel = if (workout.workoutType == WorkoutType.RUN) strings.runLabel else strings.walkLabel
            val statusLabel = if (workout.sessionState == SessionState.RUNNING) strings.trackingLive else strings.sessionPaused
            val dist = MetricUtils.formatDistanceKm(workout.distanceMeters)
            val time = MetricUtils.formatDuration(workout.elapsedMillis)
            val pace = MetricUtils.formatPace(workout.averagePaceSecPerKm)

            builder.setContentTitle("$typeLabel • $statusLabel")
            builder.setContentText("$dist km • $time • $pace/km")

            if (workout.sessionState == SessionState.RUNNING) {
                builder.addAction(
                    0,
                    strings.pauseAction,
                    serviceActionPendingIntent(ACTION_PAUSE, 1)
                )
            } else {
                builder.addAction(
                    0,
                    strings.resumeAction,
                    serviceActionPendingIntent(ACTION_RESUME, 2)
                )
            }
            builder.addAction(
                0,
                strings.finishSaveAction,
                serviceActionPendingIntent(ACTION_FINISH, 3)
            )
        } else {
            val steps = renderState?.todaySteps ?: 0
            val goal = renderState?.dailyGoal ?: 8000
            builder.setContentTitle("StridePulse • ${strings.dailyActivity}")
            builder.setContentText(
                "${MetricUtils.formatInteger(steps)} / ${MetricUtils.formatInteger(goal)} ${strings.stepsUnit}"
            )
        }

        return builder.build()
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TrackingForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateWakeLock(acquire: Boolean) {
        if (acquire) {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "StridePulse::WorkoutTrackingWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Acquire for up to 4 hours per active segment so CPU keeps ticking with screen off
                wakeLock?.acquire(4 * 60 * 60 * 1000L)
            }
        } else {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun stopForegroundTracking() {
        observerJob?.cancel()
        observerJob = null
        updateWakeLock(false)
        if (isForegroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout & Step Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps step counting and workout distance/pace running with the screen off."
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        updateWakeLock(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class ServiceRenderState(
        val workout: WorkoutTrackingSnapshot,
        val todaySteps: Int,
        val dailyGoal: Int,
        val bgSteps: Boolean,
        val language: AppLanguage
    )

    companion object {
        private const val TAG = "TrackingFgService"
        private const val CHANNEL_ID = "stride_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE"
        private const val ACTION_PAUSE = "com.example.action.PAUSE"
        private const val ACTION_RESUME = "com.example.action.RESUME"
        private const val ACTION_FINISH = "com.example.action.FINISH"
        private const val ACTION_STOP = "com.example.action.STOP"

        fun sync(context: Context, shouldRun: Boolean) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, TrackingForegroundService::class.java).apply {
                action = if (shouldRun) ACTION_START_OR_UPDATE else ACTION_STOP
            }
            try {
                if (shouldRun) {
                    ContextCompat.startForegroundService(appCtx, intent)
                } else {
                    appCtx.stopService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to sync TrackingForegroundService (shouldRun=$shouldRun): ${e.message}")
            }
        }
    }
}
