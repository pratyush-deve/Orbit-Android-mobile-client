package com.pratyush.orbitmobile.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.pratyush.orbitmobile.MainActivity
import com.pratyush.orbitmobile.OrbitMobileApp
import com.pratyush.orbitmobile.core.navigation.AppRoute
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.TransferPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * User-visible foreground service (foregroundServiceType="dataSync") active while
 * a LAN or Firebase file transfer is running. Renders Android 16+ Live Updates
 * (ProgressStyle + promoted ongoing request) on API 36+ and falls back to a standard
 * ongoing progress notification on Android 8-15.
 */
class RemoteFileTransferService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var stateObserverJob: Job? = null
    private val throttler = TransferNotificationThrottler()
    private val speedEtaEstimator = TransferSpeedEtaEstimator()

    @Volatile
    private var hasObservedActiveState = false

    @Volatile
    private var activeNotificationSessionId: String = UUID.randomUUID().toString()

    companion object {
        private const val CHANNEL_ID = "orbit_remote_transfer_channel"
        private const val RESULT_CHANNEL_ID = "orbit_remote_transfer_result_channel"
        private const val NOTIFICATION_ID = 4102
        private const val RESULT_NOTIFICATION_ID = 4103
        private const val ACTION_START = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_START"
        private const val ACTION_UPDATE = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_UPDATE"
        private const val ACTION_STOP = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_STOP"
        private const val ACTION_CANCEL = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_CANCEL"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_DETAIL = "extra_detail"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_SESSION_ID = "extra_session_id"

        fun start(context: Context, title: String, detail: String, progressPercent: Int = -1) {
            val intent = Intent(context, RemoteFileTransferService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DETAIL, detail)
                putExtra(EXTRA_PROGRESS, progressPercent)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun updateProgress(context: Context, title: String, detail: String, progressPercent: Int) {
            val intent = Intent(context, RemoteFileTransferService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DETAIL, detail)
                putExtra(EXTRA_PROGRESS, progressPercent)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RemoteFileTransferService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val requestSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (requestSessionId == null || requestSessionId == activeNotificationSessionId) {
                    (application as? OrbitMobileApp)?.appContainer?.fileTransferRepository?.cancelTransfer()
                    stopSelfSafe()
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                val isActive = (application as? OrbitMobileApp)
                    ?.appContainer
                    ?.fileTransferRepository
                    ?.isTransferActive() == true
                if (!isActive) {
                    stopSelfSafe()
                }
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Sending files to PC"
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Preparing transfer..."
                val progress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                if (intent.action == ACTION_START) {
                    activeNotificationSessionId = UUID.randomUUID().toString()
                    hasObservedActiveState = false
                    throttler.reset()
                    speedEtaEstimator.reset()
                    val content = TransferNotificationContent(
                        title = title,
                        detail = detail,
                        shortCriticalText = if (progress in 0..100) "$progress%" else "Prep",
                        progressPercent = progress.coerceAtLeast(0),
                        isIndeterminate = progress !in 0..100,
                        phase = TransferPhase.PREPARING
                    )
                    val notification = buildOngoingNotification(content)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    ensureObservingTransferState()
                } else {
                    val content = TransferNotificationContent(
                        title = title,
                        detail = detail,
                        shortCriticalText = if (progress in 0..100) "$progress%" else "Prep",
                        progressPercent = progress.coerceAtLeast(0),
                        isIndeterminate = progress !in 0..100,
                        phase = if (progress in 0..100) TransferPhase.TRANSFERRING else TransferPhase.PREPARING
                    )
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, buildOngoingNotification(content))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureObservingTransferState() {
        if (stateObserverJob?.isActive == true) return
        val repo = (application as? OrbitMobileApp)?.appContainer?.fileTransferRepository ?: return

        stateObserverJob = serviceScope.launch {
            repo.transferState.collect { state ->
                when (state) {
                    is FileTransferState.Preparing -> {
                        hasObservedActiveState = true
                        speedEtaEstimator.reset()
                        if (throttler.shouldUpdate(state)) {
                            val content = TransferNotificationThrottler.formatContent(state)
                            if (content != null) {
                                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                                nm?.notify(NOTIFICATION_ID, buildOngoingNotification(content))
                            }
                        }
                    }
                    is FileTransferState.Transferring -> {
                        hasObservedActiveState = true
                        val snapshot = speedEtaEstimator.update(state.bytesTransferred, state.totalBytes)
                        if (throttler.shouldUpdate(state)) {
                            val smoothedSpeed = if (snapshot.smoothedSpeedBytesPerSec > 0L) {
                                snapshot.smoothedSpeedBytesPerSec
                            } else {
                                state.speedBytesPerSec
                            }
                            val content = TransferNotificationThrottler.formatContent(
                                state = state,
                                smoothedSpeedBytesPerSec = smoothedSpeed,
                                etaSeconds = snapshot.etaSeconds
                            )
                            if (content != null) {
                                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                                nm?.notify(NOTIFICATION_ID, buildOngoingNotification(content))
                            }
                        }
                    }
                    is FileTransferState.Completed,
                    is FileTransferState.Failed -> {
                        if (hasObservedActiveState && !repo.isTransferActive()) {
                            stopSelfSafe()
                            postTerminalNotification(state)
                        }
                    }
                    is FileTransferState.Cancelled -> {
                        if (hasObservedActiveState && !repo.isTransferActive()) {
                            stopSelfSafe()
                        }
                    }
                    is FileTransferState.Idle -> Unit
                }
            }
        }
    }

    private fun postTerminalNotification(state: FileTransferState) {
        val terminal = TransferNotificationBuilder.buildTerminalNotification(
            context = this,
            channelId = RESULT_CHANNEL_ID,
            state = state,
            openIntent = createOpenSendFilesPendingIntent()
        ) ?: return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(RESULT_NOTIFICATION_ID, terminal)
    }

    private fun stopSelfSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress for ORBIT file transfers"
                setShowBadge(false)
            }
            val resultChannel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "File Transfer Results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a file transfer completes or fails"
            }
            nm.createNotificationChannel(ongoingChannel)
            nm.createNotificationChannel(resultChannel)
        }
    }

    private fun createOpenSendFilesPendingIntent(): PendingIntent {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, AppRoute.SendFiles.route)
        }
        return PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, RemoteFileTransferService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_SESSION_ID, activeNotificationSessionId)
        }
        return PendingIntent.getService(
            this,
            activeNotificationSessionId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildOngoingNotification(content: TransferNotificationContent): Notification {
        return TransferNotificationBuilder.buildOngoingNotification(
            context = this,
            channelId = CHANNEL_ID,
            content = content,
            openIntent = createOpenSendFilesPendingIntent(),
            cancelIntent = createCancelPendingIntent()
        )
    }
}
