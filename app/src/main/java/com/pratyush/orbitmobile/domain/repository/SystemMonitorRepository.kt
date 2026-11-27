package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.domain.model.SystemMonitorData
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository managing live hardware telemetry streaming and history retention.
 */
interface SystemMonitorRepository {
    /**
     * Observable stream of live hardware telemetry and bounded history buffers.
     */
    val systemMonitorData: StateFlow<SystemMonitorData>

    /**
     * Starts sampling hardware telemetry at approximately 1-second cadence.
     */
    fun startMonitoring()

    /**
     * Pauses telemetry polling to conserve battery/network when screen is invisible.
     */
    fun stopMonitoring()
}
