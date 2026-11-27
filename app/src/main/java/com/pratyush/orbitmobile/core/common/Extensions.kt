package com.pratyush.orbitmobile.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Utility extensions for flows, coroutines, and common types.
 */

/**
 * Wraps any Flow emission into [OrbitResult], automatically catching exceptions
 * and emitting [OrbitResult.Loading] at start.
 */
fun <T> Flow<T>.asResult(): Flow<OrbitResult<T>> {
    return this
        .map<T, OrbitResult<T>> { OrbitResult.Success(it) }
        .onStart { emit(OrbitResult.Loading) }
        .catch { emit(OrbitResult.Error(it)) }
}

/**
 * Formats a byte count into a human-readable string (KB, MB, GB).
 */
fun Long.formatAsFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        "%.1f %s",
        this / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}
