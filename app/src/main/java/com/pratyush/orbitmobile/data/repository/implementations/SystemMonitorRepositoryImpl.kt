package com.pratyush.orbitmobile.data.repository.implementations

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.firebase.FirebasePresenceManager
import com.pratyush.orbitmobile.core.firebase.FirebaseRemoteClient
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.SystemMonitorData
import com.pratyush.orbitmobile.domain.model.SystemTelemetry
import com.pratyush.orbitmobile.domain.model.TelemetryFreshness
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.SystemMonitorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production implementation of [SystemMonitorRepository].
 * Bounded history buffer (30 points), live freshness tracking, and zero seeded/fake data.
 */
class SystemMonitorRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val remoteDataSource: RemoteDataSource,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val firebaseRemoteClient: FirebaseRemoteClient? = null,
    private val firebasePresenceManager: FirebasePresenceManager? = null
) : SystemMonitorRepository {

    private val tag = "SystemMonitorRepo"
    private val historyLimit = 35

    private val bufferMutex = Mutex()
    private val cpuBuffer = ArrayDeque<Float>(historyLimit)
    private val ramBuffer = ArrayDeque<Float>(historyLimit)
    private val wifiDlBuffer = ArrayDeque<Double>(historyLimit)
    private val wifiUlBuffer = ArrayDeque<Double>(historyLimit)
    private val gpuBuffer = ArrayDeque<Float>(historyLimit)

    private var lastReceivedWallTimeMs = 0L
    private var lastTelemetry: SystemTelemetry? = null

    private val _systemMonitorData = MutableStateFlow(
        SystemMonitorData(
            telemetry = null,
            freshness = TelemetryFreshness.OFFLINE
        )
    )
    override val systemMonitorData: StateFlow<SystemMonitorData> = _systemMonitorData.asStateFlow()

    private var pollJob: Job? = null
    private var remoteTelemetryDetach: (() -> Unit)? = null
    private var activeSubscribersCount = 0

    init {
        // Observe connection state transitions
        scope.launch {
            connectionRepository.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    stopRemoteTelemetryListener()
                } else if (activeSubscribersCount > 0 && firebaseRemoteClient?.isRemoteAvailable == true) {
                    startRemoteTelemetryListener()
                } else {
                    _systemMonitorData.update { current ->
                        current.copy(freshness = TelemetryFreshness.OFFLINE)
                    }
                }
            }
        }
    }

    override fun startMonitoring() {
        activeSubscribersCount++
        if (pollJob == null || pollJob?.isActive != true) {
            startPollingLoop()
        }
        if (connectionRepository.connectionState.value !is ConnectionState.Connected &&
            firebaseRemoteClient?.isRemoteAvailable == true
        ) {
            startRemoteTelemetryListener()
        }
    }

    override fun stopMonitoring() {
        activeSubscribersCount = maxOf(0, activeSubscribersCount - 1)
        if (activeSubscribersCount == 0) {
            pollJob?.cancel()
            pollJob = null
            stopRemoteTelemetryListener()
        }
    }

    private fun startRemoteTelemetryListener() {
        if (remoteTelemetryDetach != null) return
        remoteTelemetryDetach = firebaseRemoteClient?.observeTelemetry { telemetry ->
            scope.launch {
                val age = System.currentTimeMillis() - telemetry.timestampMs
                val freshness = when {
                    age > 35_000L -> TelemetryFreshness.OFFLINE
                    age > 12_000L -> TelemetryFreshness.STALE
                    else -> TelemetryFreshness.LIVE
                }
                lastReceivedWallTimeMs = telemetry.timestampMs
                lastTelemetry = telemetry
                bufferMutex.withLock {
                    appendToBuffer(cpuBuffer, telemetry.cpu.utilizationPercent)
                    appendToBuffer(ramBuffer, telemetry.ram.utilizationPercent)
                    appendToBuffer(wifiDlBuffer, telemetry.wifi.downloadSpeedBps)
                    appendToBuffer(wifiUlBuffer, telemetry.wifi.uploadSpeedBps)
                    appendToBuffer(gpuBuffer, telemetry.gpu.utilizationPercent)
                    _systemMonitorData.value = SystemMonitorData(
                        telemetry = telemetry,
                        freshness = freshness,
                        cpuHistory = cpuBuffer.toList(),
                        ramHistory = ramBuffer.toList(),
                        wifiDownloadHistory = wifiDlBuffer.toList(),
                        wifiUploadHistory = wifiUlBuffer.toList(),
                        gpuHistory = gpuBuffer.toList()
                    )
                }
            }
        }
    }

    private fun stopRemoteTelemetryListener() {
        remoteTelemetryDetach?.invoke()
        remoteTelemetryDetach = null
    }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val isConnected = connectionRepository.connectionState.value is ConnectionState.Connected
                val host = connectionRepository.activeHost
                val port = connectionRepository.activePort
                val token = connectionRepository.activeAuthToken

                if (!isConnected || host == null || token == null) {
                    if (firebaseRemoteClient?.isRemoteAvailable == true) {
                        startRemoteTelemetryListener()
                        if (lastReceivedWallTimeMs > 0L) {
                            val age = System.currentTimeMillis() - lastReceivedWallTimeMs
                            val f = when {
                                age > 35_000L -> TelemetryFreshness.OFFLINE
                                age > 12_000L -> TelemetryFreshness.STALE
                                else -> TelemetryFreshness.LIVE
                            }
                            _systemMonitorData.update { it.copy(freshness = f) }
                        }
                    } else {
                        stopRemoteTelemetryListener()
                        _systemMonitorData.update { it.copy(freshness = TelemetryFreshness.OFFLINE) }
                    }
                    delay(3500L)
                    continue
                }
                stopRemoteTelemetryListener()

                var nextDelayMs = 1000L
                try {
                    val result = remoteDataSource.fetchSystemStats(host, port, token)
                    when (result) {
                        is OrbitResult.Success -> {
                            val telemetry = result.data
                            lastReceivedWallTimeMs = System.currentTimeMillis()
                            lastTelemetry = telemetry

                            bufferMutex.withLock {
                                appendToBuffer(cpuBuffer, telemetry.cpu.utilizationPercent)
                                appendToBuffer(ramBuffer, telemetry.ram.utilizationPercent)
                                appendToBuffer(wifiDlBuffer, telemetry.wifi.downloadSpeedBps)
                                appendToBuffer(wifiUlBuffer, telemetry.wifi.uploadSpeedBps)
                                appendToBuffer(gpuBuffer, telemetry.gpu.utilizationPercent)

                                _systemMonitorData.value = SystemMonitorData(
                                    telemetry = telemetry,
                                    freshness = TelemetryFreshness.LIVE,
                                    cpuHistory = cpuBuffer.toList(),
                                    ramHistory = ramBuffer.toList(),
                                    wifiDownloadHistory = wifiDlBuffer.toList(),
                                    wifiUploadHistory = wifiUlBuffer.toList(),
                                    gpuHistory = gpuBuffer.toList()
                                )
                            }
                        }

                        is OrbitResult.Error -> {
                            Log.w(tag, "Fetch error: ${result.exception.message}")
                            evaluateFreshness()
                            nextDelayMs = if (_systemMonitorData.value.freshness == TelemetryFreshness.OFFLINE) 4000L else 2500L
                        }

                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Polling exception: ${e.message}")
                    evaluateFreshness()
                    nextDelayMs = 2500L
                }

                delay(nextDelayMs)
            }
        }
    }

    private fun evaluateFreshness() {
        val age = System.currentTimeMillis() - lastReceivedWallTimeMs
        val freshness = when {
            lastReceivedWallTimeMs == 0L -> TelemetryFreshness.OFFLINE
            age > 7000L -> TelemetryFreshness.OFFLINE
            age > 2800L -> TelemetryFreshness.STALE
            else -> TelemetryFreshness.LIVE
        }

        _systemMonitorData.update { it.copy(freshness = freshness) }
    }

    private fun <T> appendToBuffer(buffer: ArrayDeque<T>, item: T) {
        if (buffer.size >= historyLimit) {
            buffer.removeFirst()
        }
        buffer.addLast(item)
    }
}
