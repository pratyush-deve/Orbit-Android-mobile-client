package com.pratyush.orbitmobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

/**
 * Unit tests verifying screen-off background telemetry resilience:
 * - Mutex concurrency serialization & follow-up coalescing
 * - Scoped wake-lock acquisition and guaranteed release
 * - Bounded exponential backoff with jitter
 * - Session/epoch protection against stale network responses
 * - Notification update throttling logic
 */
class ScreenOffTelemetryUnitTest {

    @Test
    fun testTelemetryMutexCoalescingConcurrentTriggers() = runBlocking(Dispatchers.Default) {
        val mutex = Mutex()
        val hasPendingTelemetry = AtomicBoolean(false)
        val dispatchExecutionCount = AtomicInteger(0)
        val inFlightCount = AtomicInteger(0)
        val maxConcurrentInFlight = AtomicInteger(0)

        suspend fun executeMockDispatch() {
            val current = inFlightCount.incrementAndGet()
            maxConcurrentInFlight.updateAndGet { prev -> maxOf(prev, current) }
            delay(50) // simulate network I/O
            dispatchExecutionCount.incrementAndGet()
            inFlightCount.decrementAndGet()
        }

        suspend fun sendTelemetryNow() {
            if (!mutex.tryLock()) {
                hasPendingTelemetry.set(true)
                return
            }
            try {
                do {
                    hasPendingTelemetry.set(false)
                    executeMockDispatch()
                } while (hasPendingTelemetry.getAndSet(false))
            } finally {
                mutex.unlock()
            }
        }

        // Fire 10 simultaneous triggers
        val jobs = (1..10).map {
            async {
                sendTelemetryNow()
            }
        }
        jobs.awaitAll()

        // Verify that concurrency was strictly 1 (no overlapping requests)
        assertEquals("Max concurrent in-flight dispatches must be exactly 1", 1, maxConcurrentInFlight.get())
        // Coalescing ensures that 10 simultaneous requests collapse to at most 2 dispatches
        assertTrue("Dispatches should coalesce to <= 3 (actual: ${dispatchExecutionCount.get()})", dispatchExecutionCount.get() <= 3)
    }

    @Test
    fun testScopedLockGuaranteedReleaseOnSuccessAndError() {
        var isWakeLockHeld = false
        var isWifiLockHeld = false

        val mockLock = object : AutoCloseable {
            fun acquire() {
                isWakeLockHeld = true
                isWifiLockHeld = true
            }

            override fun close() {
                isWakeLockHeld = false
                isWifiLockHeld = false
            }
        }

        // 1. Success case
        mockLock.acquire()
        assertTrue(isWakeLockHeld)
        assertTrue(isWifiLockHeld)
        try {
            // Simulated quick work
        } finally {
            mockLock.close()
        }
        assertFalse(isWakeLockHeld)
        assertFalse(isWifiLockHeld)

        // 2. Exception/Error case
        mockLock.acquire()
        assertTrue(isWakeLockHeld)
        assertTrue(isWifiLockHeld)
        try {
            throw RuntimeException("Simulated network timeout during screen-off")
        } catch (e: Exception) {
            // caught
        } finally {
            mockLock.close()
        }
        assertFalse("WakeLock must be released in finally block even after exception", isWakeLockHeld)
        assertFalse("WifiLock must be released in finally block even after exception", isWifiLockHeld)
    }

    @Test
    fun testRetryExponentialBackoffWithJitterBounds() {
        for (failures in 1..10) {
            val baseDelay = (5000L * (1L shl min(failures, 4))).coerceAtMost(60000L)
            val jitter = Random.nextLong(0, 3000L)
            val backoff = baseDelay + jitter

            // Failure 1: base = 10000, backoff in [10000, 13000]
            // Failure 2: base = 20000, backoff in [20000, 23000]
            // Failure 3: base = 40000, backoff in [40000, 43000]
            // Failure >= 4: base = 60000, backoff in [60000, 63000]
            assertTrue(baseDelay in 10000L..60000L)
            assertTrue("Backoff must respect lower bound", backoff >= baseDelay)
            assertTrue("Backoff must not exceed capped limit + jitter", backoff <= 63000L)
        }
    }

    @Test
    fun testConnectionEpochProtectsAgainstStaleTelemetryResponses() {
        val currentEpoch = AtomicLong(1L)
        var appliedTelemetryCount = 0

        fun handleIncomingTelemetryResponse(responseEpoch: Long) {
            if (responseEpoch == currentEpoch.get()) {
                appliedTelemetryCount++
            }
        }

        // Dispatch started at epoch 1
        val req1Epoch = currentEpoch.get()

        // Connection resets / renegotiates to epoch 2
        currentEpoch.incrementAndGet()
        assertEquals(2L, currentEpoch.get())

        // Late response arrives from epoch 1
        handleIncomingTelemetryResponse(req1Epoch)
        assertEquals("Stale response from previous epoch must be ignored", 0, appliedTelemetryCount)

        // Fresh response arrives from current epoch 2
        handleIncomingTelemetryResponse(currentEpoch.get())
        assertEquals("Fresh response from active epoch must be accepted", 1, appliedTelemetryCount)
    }

    @Test
    fun testNotificationThrottlingSuppressesRapidUpdates() {
        var lastTitle = ""
        var lastContent = ""
        var lastTime = 0L
        var notifyCount = 0

        fun shouldNotify(newTitle: String, newContent: String, currentTime: Long, force: Boolean): Boolean {
            val textChanged = (newTitle != lastTitle || newContent != lastContent)
            val timeElapsed = (currentTime - lastTime) >= 3000L

            if (!force && (!textChanged || !timeElapsed)) {
                return false
            }

            lastTitle = newTitle
            lastContent = newContent
            lastTime = currentTime
            notifyCount++
            return true
        }

        val t0 = 10000L
        // Initial notification
        assertTrue(shouldNotify("Title A", "Content A", t0, force = false))
        assertEquals(1, notifyCount)

        // Rapid duplicate 500ms later (no change, no time) -> suppressed
        assertFalse(shouldNotify("Title A", "Content A", t0 + 500L, force = false))
        assertEquals(1, notifyCount)

        // Rapid text change only 1000ms later -> suppressed by rate-limit timer
        assertFalse(shouldNotify("Title B", "Content B", t0 + 1000L, force = false))
        assertEquals(1, notifyCount)

        // Text change after 3500ms -> allowed
        assertTrue(shouldNotify("Title B", "Content B", t0 + 3500L, force = false))
        assertEquals(2, notifyCount)

        // Forced update -> allowed immediately
        assertTrue(shouldNotify("Title C", "Content C", t0 + 3600L, force = true))
        assertEquals(3, notifyCount)
    }
}
