package com.pratyush.orbitmobile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated utility functions for date and duration formatting.
 */
object DateUtils {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTime(epochMs: Long): String = timeFormat.format(Date(epochMs))

    fun formatDateTime(epochMs: Long): String = dateTimeFormat.format(Date(epochMs))

    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02dm %02ds", minutes, seconds)
        }
    }
}
