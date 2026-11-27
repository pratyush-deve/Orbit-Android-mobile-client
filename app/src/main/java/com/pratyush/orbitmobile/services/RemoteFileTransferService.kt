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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pratyush.orbitmobile.MainActivity
import com.pratyush.orbitmobile.OrbitMobileApp
import com.pratyush.orbitmobile.R

/**
 * User-visible foreground service (foregroundServiceType="dataSync") active ONLY while
 * a remote file transfer is running, ensuring Android 17 / ColorOS 17 does not throttle
 * or freeze network sockets during backgrounded uploads.
 */
class RemoteFileTransferService : Service() {

    companion object {
        private const val CHANNEL_ID = "orbit_remote_transfer_channel"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_START"
        private const val ACTION_UPDATE = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_UPDATE"
        private const val ACTION_STOP = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_STOP"
        private const val ACTION_CANCEL = "com.pratyush.orbitmobile.action.REMOTE_TRANSFER_CANCEL"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_DETAIL = "extra_detail"
        private const val EXTRA_PROGRESS = "extra_progress"

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
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                (application as? OrbitMobileApp)?.appContainer?.fileTransferRepository?.cancelTransfer()
                stopSelfSafe()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelfSafe()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Sending files to PC"
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Uploading encrypted file..."
                val progress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                val notification = buildNotification(title, detail, progress)
                if (intent.action == ACTION_START) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
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

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for encrypted remote file transfers to ORBIT Windows"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, detail: String, progressPercent: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RemoteFileTransferService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.orbit_logo)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", cancelIntent)

        if (progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }
}
