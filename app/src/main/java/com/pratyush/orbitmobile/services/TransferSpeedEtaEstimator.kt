package com.pratyush.orbitmobile.services

data class SpeedEtaSnapshot(
    val smoothedSpeedBytesPerSec: Long,
    val etaSeconds: Long?
)

/**
 * Computes an Exponential Moving Average (EMA) transfer speed and estimated remaining time (ETA)
 * without polluting domain transfer state.
 */
class TransferSpeedEtaEstimator(
    private val alpha: Double = 0.30,
    private val minSampleWindowMs: Long = 500L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private var lastSampleTimeMs: Long = 0L
    private var lastSampleBytes: Long = 0L
    private var emaSpeedBytesPerSec: Double = 0.0

    @Synchronized
    fun update(bytesTransferred: Long, totalBytes: Long): SpeedEtaSnapshot {
        val now = clockMs()
        if (lastSampleTimeMs == 0L) {
            lastSampleTimeMs = now
            lastSampleBytes = bytesTransferred
            return SpeedEtaSnapshot(0L, null)
        }

        val elapsedMs = now - lastSampleTimeMs
        val deltaBytes = (bytesTransferred - lastSampleBytes).coerceAtLeast(0L)
        if (elapsedMs >= minSampleWindowMs && deltaBytes > 0L) {
            val instantSpeed = (deltaBytes * 1000.0) / elapsedMs.toDouble()
            emaSpeedBytesPerSec = if (emaSpeedBytesPerSec <= 0.0) {
                instantSpeed
            } else {
                (alpha * instantSpeed) + ((1.0 - alpha) * emaSpeedBytesPerSec)
            }
            lastSampleTimeMs = now
            lastSampleBytes = bytesTransferred
        }

        val speed = emaSpeedBytesPerSec.toLong().coerceAtLeast(0L)
        val remainingBytes = (totalBytes - bytesTransferred).coerceAtLeast(0L)
        val etaSeconds = if (speed > 0L && remainingBytes > 0L) {
            (remainingBytes + speed - 1L) / speed
        } else {
            null
        }
        return SpeedEtaSnapshot(speed, etaSeconds)
    }

    @Synchronized
    fun reset() {
        lastSampleTimeMs = 0L
        lastSampleBytes = 0L
        emaSpeedBytesPerSec = 0.0
    }

    companion object {
        fun formatEta(etaSeconds: Long): String {
            if (etaSeconds <= 0L) return ""
            val hours = etaSeconds / 3600L
            val minutes = (etaSeconds % 3600L) / 60L
            val seconds = etaSeconds % 60L
            return when {
                hours > 0L -> "~${hours}h ${minutes}m left"
                minutes > 0L -> "~${minutes}m ${seconds}s left"
                else -> "~${seconds}s left"
            }
        }
    }
}
