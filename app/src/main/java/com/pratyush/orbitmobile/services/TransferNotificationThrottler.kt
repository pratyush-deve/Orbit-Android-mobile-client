package com.pratyush.orbitmobile.services

import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import com.pratyush.orbitmobile.domain.model.TransferPhase

data class TransferNotificationContent(
    val title: String,
    val detail: String,
    val shortCriticalText: String,
    val progressPercent: Int,
    val isIndeterminate: Boolean,
    val phase: TransferPhase
)

/**
 * Rate-limits foreground notification updates during high-frequency chunked transfers
 * while allowing immediate updates for preparation, phase transitions, and terminal states.
 */
class TransferNotificationThrottler(
    private val minIntervalMs: Long = 1000L,
    private val maxStaleIntervalMs: Long = 2500L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private var lastUpdateMs: Long = 0L
    private var lastPercent: Int = -1
    private var lastFileIndex: Int = -1
    private var lastPhase: TransferPhase? = null

    @Synchronized
    fun shouldUpdate(state: FileTransferState): Boolean {
        val now = clockMs()
        return when (state) {
            is FileTransferState.Preparing -> {
                val phaseChanged = state.phase != lastPhase
                lastUpdateMs = now
                lastPercent = -1
                lastFileIndex = -1
                lastPhase = state.phase
                phaseChanged || true
            }
            is FileTransferState.Completed,
            is FileTransferState.Failed,
            is FileTransferState.Cancelled -> {
                lastUpdateMs = now
                lastPercent = -1
                lastFileIndex = -1
                lastPhase = null
                true
            }
            is FileTransferState.Transferring -> {
                val percent = (state.overallProgress * 100).toInt().coerceIn(0, 100)
                val elapsed = now - lastUpdateMs
                val isFirst = lastUpdateMs == 0L
                val fileChanged = state.currentFileIndex != lastFileIndex
                val phaseChanged = state.phase != lastPhase
                val percentChanged = percent != lastPercent

                val allow = isFirst ||
                    fileChanged ||
                    phaseChanged ||
                    (elapsed >= minIntervalMs && (percentChanged || elapsed >= maxStaleIntervalMs))

                if (allow) {
                    lastUpdateMs = now
                    lastPercent = percent
                    lastFileIndex = state.currentFileIndex
                    lastPhase = state.phase
                }
                allow
            }
            is FileTransferState.Idle -> false
        }
    }

    @Synchronized
    fun reset() {
        lastUpdateMs = 0L
        lastPercent = -1
        lastFileIndex = -1
        lastPhase = null
    }

    companion object {
        fun formatContent(
            state: FileTransferState,
            smoothedSpeedBytesPerSec: Long? = null,
            etaSeconds: Long? = null
        ): TransferNotificationContent? {
            return when (state) {
                is FileTransferState.Preparing -> {
                    val title = if (state.phase == TransferPhase.ENCRYPTING) {
                        "Encrypting ${state.fileName}"
                    } else {
                        "Preparing ${state.fileName}"
                    }
                    val detail = if (state.phase == TransferPhase.ENCRYPTING) {
                        "Encrypting for secure remote transfer..."
                    } else {
                        "Preparing transfer..."
                    }
                    TransferNotificationContent(
                        title = title,
                        detail = detail,
                        shortCriticalText = "Prep",
                        progressPercent = 0,
                        isIndeterminate = true,
                        phase = state.phase
                    )
                }
                is FileTransferState.Transferring -> {
                    val percent = (state.overallProgress * 100).toInt().coerceIn(0, 100)
                    val transferred = SelectedFileItem.formatBytes(state.bytesTransferred)
                    val total = SelectedFileItem.formatBytes(state.totalBytes)

                    if (state.phase == TransferPhase.FINISHING_ON_PC) {
                        val title = if (state.totalFiles > 1) {
                            "Finishing on PC (${state.currentFileIndex}/${state.totalFiles})"
                        } else {
                            "Finishing ${state.currentFileName} on PC"
                        }
                        return TransferNotificationContent(
                            title = title,
                            detail = "$transferred / $total · Waiting for Windows to verify...",
                            shortCriticalText = "PC...",
                            progressPercent = percent.coerceAtMost(99),
                            isIndeterminate = true,
                            phase = TransferPhase.FINISHING_ON_PC
                        )
                    }

                    val effectiveSpeed = smoothedSpeedBytesPerSec ?: state.speedBytesPerSec
                    val parts = mutableListOf("$transferred / $total")
                    if (effectiveSpeed > 0L) {
                        parts.add("${SelectedFileItem.formatBytes(effectiveSpeed)}/s")
                    }
                    if (etaSeconds != null && etaSeconds > 0L) {
                        val etaText = TransferSpeedEtaEstimator.formatEta(etaSeconds)
                        if (etaText.isNotEmpty()) parts.add(etaText)
                    }

                    val title = if (state.totalFiles > 1) {
                        "Sending ${state.currentFileName} (${state.currentFileIndex}/${state.totalFiles})"
                    } else {
                        "Sending ${state.currentFileName}"
                    }

                    TransferNotificationContent(
                        title = title,
                        detail = parts.joinToString(" · "),
                        shortCriticalText = "$percent%",
                        progressPercent = percent,
                        isIndeterminate = false,
                        phase = state.phase
                    )
                }
                else -> null
            }
        }
    }
}
