package com.pratyush.orbitmobile.services

import android.app.PendingIntent
import android.content.ContextWrapper
import android.content.Intent
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.TransferPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdatesNotificationUnitTest {

    @Test
    fun apiLevelGuardMatchesAndroid16Threshold() {
        assertFalse(TransferNotificationBuilder.isLiveUpdateSupported(26))
        assertFalse(TransferNotificationBuilder.isLiveUpdateSupported(34))
        assertFalse(TransferNotificationBuilder.isLiveUpdateSupported(35))
        assertTrue(TransferNotificationBuilder.isLiveUpdateSupported(36))
        assertTrue(TransferNotificationBuilder.isLiveUpdateSupported(37))
    }

    @Test
    fun compactCriticalTextReflectsAllTransferPhases() {
        val prep = TransferNotificationThrottler.formatContent(
            FileTransferState.Preparing("archive.zip", TransferPhase.PREPARING)
        )!!
        assertEquals("Prep", prep.shortCriticalText)
        assertTrue(prep.isIndeterminate)

        val enc = TransferNotificationThrottler.formatContent(
            FileTransferState.Preparing("archive.zip", TransferPhase.ENCRYPTING)
        )!!
        assertEquals("Prep", enc.shortCriticalText)
        assertTrue(enc.title.contains("Encrypting"))

        val uploading = TransferNotificationThrottler.formatContent(
            state = FileTransferState.Transferring(
                currentFileIndex = 1,
                totalFiles = 1,
                currentFileName = "archive.zip",
                currentFileProgress = 0.25f,
                overallProgress = 0.25f,
                bytesTransferred = 512L * 1024L * 1024L,
                totalBytes = 2L * 1024L * 1024L * 1024L,
                speedBytesPerSec = 8L * 1024L * 1024L,
                phase = TransferPhase.TRANSFERRING
            ),
            smoothedSpeedBytesPerSec = 8L * 1024L * 1024L,
            etaSeconds = 192L
        )!!
        assertEquals("25%", uploading.shortCriticalText)
        assertEquals("Sending archive.zip", uploading.title)
        assertTrue(uploading.detail.contains("512.0 MB / 2.0 GB"))
        assertTrue(uploading.detail.contains("8.0 MB/s"))
        assertTrue(uploading.detail.contains("~3m 12s left"))
        assertFalse(uploading.isIndeterminate)

        val finishing = TransferNotificationThrottler.formatContent(
            FileTransferState.Transferring(
                currentFileIndex = 1,
                totalFiles = 1,
                currentFileName = "archive.zip",
                currentFileProgress = 1.0f,
                overallProgress = 1.0f,
                bytesTransferred = 2L * 1024L * 1024L * 1024L,
                totalBytes = 2L * 1024L * 1024L * 1024L,
                speedBytesPerSec = 8L * 1024L * 1024L,
                phase = TransferPhase.FINISHING_ON_PC
            )
        )!!
        assertEquals("PC...", finishing.shortCriticalText)
        assertTrue(finishing.progressPercent <= 99)
        assertTrue(finishing.isIndeterminate)
    }

    @Test
    fun multiFileFormatDistinguishesCurrentFileFromOverallProgress() {
        val content = TransferNotificationThrottler.formatContent(
            FileTransferState.Transferring(
                currentFileIndex = 2,
                totalFiles = 4,
                currentFileName = "video.mp4",
                currentFileProgress = 0.50f,
                overallProgress = 0.35f,
                bytesTransferred = 350L * 1024L * 1024L,
                totalBytes = 1000L * 1024L * 1024L,
                speedBytesPerSec = 10L * 1024L * 1024L
            )
        )!!
        assertEquals("Sending video.mp4 (2/4)", content.title)
        assertEquals("35%", content.shortCriticalText)
    }

    @Test
    fun emaSpeedAndEtaEstimatorSmoothsSpeedAndAvoidsMisleadingEta() {
        var now = 1_000L
        val estimator = TransferSpeedEtaEstimator(alpha = 0.30, minSampleWindowMs = 500L, clockMs = { now })

        // Initial sample -> no speed or ETA yet
        val s0 = estimator.update(0L, 1_000L)
        assertEquals(0L, s0.smoothedSpeedBytesPerSec)
        assertNull(s0.etaSeconds)

        // 1 second later, 200 bytes transferred -> 200 B/s, 800 remaining -> 4s
        now = 2_000L
        val s1 = estimator.update(200L, 1_000L)
        assertEquals(200L, s1.smoothedSpeedBytesPerSec)
        assertEquals(4L, s1.etaSeconds)

        // 1 second later, 100 bytes transferred -> EMA = 0.3*100 + 0.7*200 = 170 B/s
        now = 3_000L
        val s2 = estimator.update(300L, 1_000L)
        assertEquals(170L, s2.smoothedSpeedBytesPerSec)
        assertNotNull(s2.etaSeconds)

        // At completion -> ETA is null
        now = 4_000L
        val sDone = estimator.update(1_000L, 1_000L)
        assertNull(sDone.etaSeconds)

        assertEquals("~3m 12s left", TransferSpeedEtaEstimator.formatEta(192L))
        assertEquals("~45s left", TransferSpeedEtaEstimator.formatEta(45L))
        assertEquals("~1h 1m left", TransferSpeedEtaEstimator.formatEta(3660L))
    }

    @Test
    fun terminalNotificationFormattingHandlesCompletedFailedAndCancelled() {
        val completed = TransferNotificationBuilder.formatTerminalNotificationText(
            FileTransferState.Completed(
                totalFiles = 1,
                totalBytes = 1024L * 1024L,
                primaryFileName = "archive.zip"
            )
        )!!
        assertEquals("Transfer complete", completed.first)
        assertTrue(completed.second.contains("archive.zip"))

        val failed = TransferNotificationBuilder.formatTerminalNotificationText(
            FileTransferState.Failed("Network timeout")
        )!!
        assertEquals("File transfer failed", failed.first)
        assertEquals("Network timeout", failed.second)

        // Cancelled must NOT produce a completion/failure notification
        assertNull(TransferNotificationBuilder.formatTerminalNotificationText(FileTransferState.Cancelled))
    }

    @Test
    fun compatFallbackGuardsLiveUpdateApisOnOlderAndroid() {
        val context = ContextStub()
        assertFalse(TransferNotificationBuilder.isLiveUpdateSupported(35))
        assertFalse(TransferNotificationBuilder.canPostPromotedNotifications(context, 35))
        assertFalse(TransferNotificationBuilder.canPostPromotedNotifications(context, 36))
    }

    private class ContextStub : ContextWrapper(null)
}
