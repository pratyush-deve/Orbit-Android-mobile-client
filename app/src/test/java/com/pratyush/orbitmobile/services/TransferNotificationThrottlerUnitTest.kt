package com.pratyush.orbitmobile.services

import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.TransferPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNotificationThrottlerUnitTest {

    @Test
    fun throttlerAllowsImmediatePreparingAndTerminalTransitions() {
        var now = 1_000L
        val throttler = TransferNotificationThrottler(clockMs = { now })

        assertTrue(throttler.shouldUpdate(FileTransferState.Preparing("a.zip")))

        val t1 = transferring(progress = 0.01f, bytes = 100)
        assertTrue(throttler.shouldUpdate(t1))

        // Within 1000ms, regular progress update is suppressed
        now = 1_200L
        assertFalse(throttler.shouldUpdate(transferring(progress = 0.05f, bytes = 500)))

        // Terminal states always bypass throttle
        assertTrue(throttler.shouldUpdate(FileTransferState.Completed(1, 1000)))
        assertTrue(throttler.shouldUpdate(FileTransferState.Failed("err")))
        assertTrue(throttler.shouldUpdate(FileTransferState.Cancelled))
    }

    @Test
    fun throttlerLimitsToOneUpdatePerSecondAndRefreshesOnStaleInterval() {
        var now = 10_000L
        val throttler = TransferNotificationThrottler(
            minIntervalMs = 1000L,
            maxStaleIntervalMs = 2500L,
            clockMs = { now }
        )

        // First transferring update is allowed immediately
        assertTrue(throttler.shouldUpdate(transferring(progress = 0.10f, bytes = 100)))

        // 500ms later with different percent -> blocked
        now = 10_500L
        assertFalse(throttler.shouldUpdate(transferring(progress = 0.12f, bytes = 120)))

        // 1000ms later with different percent -> allowed
        now = 11_000L
        assertTrue(throttler.shouldUpdate(transferring(progress = 0.12f, bytes = 120)))

        // 1000ms later with SAME percent -> blocked (no meaningful progress change yet)
        now = 12_000L
        assertFalse(throttler.shouldUpdate(transferring(progress = 0.12f, bytes = 125)))

        // 2500ms after last allowed update (11_000 + 2500 = 13_500) -> allowed even with same percent
        now = 13_500L
        assertTrue(throttler.shouldUpdate(transferring(progress = 0.12f, bytes = 128)))
    }

    @Test
    fun throttlerAllowsImmediateUpdateWhenFileIndexChangesAndFormatsContent() {
        var now = 5_000L
        val throttler = TransferNotificationThrottler(clockMs = { now })

        val file1 = transferring(fileIndex = 1, totalFiles = 2, progress = 0.50f, bytes = 512, total = 1024, speed = 2048)
        assertTrue(throttler.shouldUpdate(file1))

        now = 5_100L
        val file2 = transferring(fileIndex = 2, totalFiles = 2, progress = 0.51f, bytes = 520, total = 1024, speed = 2048)
        assertTrue(throttler.shouldUpdate(file2))

        val formatted = TransferNotificationThrottler.formatContent(file2)
        assertNotNull(formatted)
        assertEquals(51, formatted!!.progressPercent)
        assertFalse(formatted.isIndeterminate)
        assertTrue(formatted.title.contains("2/2"))
        assertTrue(formatted.detail.contains("51%").not())
        assertEquals("51%", formatted.shortCriticalText)

        // Phase transition to FINISHING_ON_PC bypasses 1s throttle immediately
        now = 5_150L
        val finishing = transferring(
            fileIndex = 2,
            totalFiles = 2,
            progress = 1.0f,
            bytes = 1024,
            total = 1024,
            speed = 2048,
            phase = TransferPhase.FINISHING_ON_PC
        )
        assertTrue(throttler.shouldUpdate(finishing))
    }

    private fun transferring(
        fileIndex: Int = 1,
        totalFiles: Int = 1,
        progress: Float = 0.5f,
        bytes: Long = 500L,
        total: Long = 1000L,
        speed: Long = 1024L,
        phase: TransferPhase = TransferPhase.TRANSFERRING
    ) = FileTransferState.Transferring(
        currentFileIndex = fileIndex,
        totalFiles = totalFiles,
        currentFileName = "archive.zip",
        currentFileProgress = progress,
        overallProgress = progress,
        bytesTransferred = bytes,
        totalBytes = total,
        speedBytesPerSec = speed,
        phase = phase
    )
}
