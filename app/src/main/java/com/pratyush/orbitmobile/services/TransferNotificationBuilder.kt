package com.pratyush.orbitmobile.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pratyush.orbitmobile.R
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import com.pratyush.orbitmobile.domain.model.TransferPhase

/**
 * Builds Android 16+ (API 36+) Live Update notifications using [Notification.ProgressStyle]
 * and promoted ongoing requests, with a standard [NotificationCompat] fallback for API 26-35.
 */
object TransferNotificationBuilder {

    fun isLiveUpdateSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= 36

    fun canPostPromotedNotifications(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (!isLiveUpdateSupported(sdkInt)) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
    }

    fun buildOngoingNotification(
        context: Context,
        channelId: String,
        content: TransferNotificationContent,
        openIntent: PendingIntent?,
        cancelIntent: PendingIntent?,
        sdkInt: Int = Build.VERSION.SDK_INT,
        requestPromotion: Boolean = canPostPromotedNotifications(context, sdkInt)
    ): Notification {
        return if (isLiveUpdateSupported(sdkInt)) {
            buildAndroid16LiveUpdate(
                context = context,
                channelId = channelId,
                content = content,
                openIntent = openIntent,
                cancelIntent = cancelIntent,
                requestPromotion = requestPromotion
            )
        } else {
            buildCompatOngoing(
                context = context,
                channelId = channelId,
                content = content,
                openIntent = openIntent,
                cancelIntent = cancelIntent
            )
        }
    }

    @RequiresApi(36)
    internal fun buildAndroid16LiveUpdate(
        context: Context,
        channelId: String,
        content: TransferNotificationContent,
        openIntent: PendingIntent?,
        cancelIntent: PendingIntent?,
        requestPromotion: Boolean
    ): Notification {
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressStartIcon(Icon.createWithResource(context, R.drawable.ic_transfer_phone))
            .setProgressEndIcon(Icon.createWithResource(context, R.drawable.ic_transfer_laptop))
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_transfer_upload))

        if (content.isIndeterminate) {
            progressStyle.setProgressIndeterminate(true)
        } else {
            progressStyle.setProgress(content.progressPercent.coerceIn(0, 100))
        }

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.orbit_logo)
            .setContentTitle(content.title)
            .setContentText(content.detail)
            .setSubText(
                if (content.phase == TransferPhase.FINISHING_ON_PC) "ORBIT · Verifying" else "ORBIT Transfer"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(progressStyle)
            .setShortCriticalText(content.shortCriticalText)
            .setRequestPromotedOngoing(requestPromotion)

        if (openIntent != null) {
            builder.setContentIntent(openIntent)
        }
        if (cancelIntent != null) {
            builder.addAction(Notification.Action.Builder(null, "Cancel", cancelIntent).build())
        }

        return builder.build()
    }

    internal fun buildCompatOngoing(
        context: Context,
        channelId: String,
        content: TransferNotificationContent,
        openIntent: PendingIntent?,
        cancelIntent: PendingIntent?
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.orbit_logo)
            .setContentTitle(content.title)
            .setContentText(content.detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (openIntent != null) {
            builder.setContentIntent(openIntent)
        }
        if (cancelIntent != null) {
            builder.addAction(0, "Cancel", cancelIntent)
        }

        if (content.isIndeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, content.progressPercent.coerceIn(0, 100), false)
        }
        return builder.build()
    }

    fun formatTerminalNotificationText(state: FileTransferState): Pair<String, String>? {
        return when (state) {
            is FileTransferState.Completed -> {
                val sizeText = SelectedFileItem.formatBytes(state.totalBytes)
                if (state.failedFiles.isEmpty()) {
                    val fileLabel = if (state.totalFiles == 1 && !state.primaryFileName.isNullOrBlank()) {
                        state.primaryFileName
                    } else {
                        "${state.totalFiles} file(s)"
                    }
                    "Transfer complete" to "Sent $fileLabel ($sizeText) to PC"
                } else {
                    "Transfer finished with errors" to
                        "${state.totalFiles} sent, ${state.failedFiles.size} failed ($sizeText)"
                }
            }
            is FileTransferState.Failed -> {
                "File transfer failed" to state.error.ifBlank { "Transfer could not be completed" }
            }
            else -> null
        }
    }

    fun buildTerminalNotification(
        context: Context,
        channelId: String,
        state: FileTransferState,
        openIntent: PendingIntent
    ): Notification? {
        val (title, detail) = formatTerminalNotificationText(state) ?: return null
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.orbit_logo)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(openIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
