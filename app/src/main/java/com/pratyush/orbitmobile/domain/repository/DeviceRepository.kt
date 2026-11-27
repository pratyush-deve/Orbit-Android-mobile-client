package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for managing paired and discovered devices.
 */
interface DeviceRepository {
    fun getDiscoveredDevices(): Flow<List<Device>>
    fun getPairedDevices(): Flow<List<Device>>
    suspend fun saveDevice(device: Device): OrbitResult<Unit>
    suspend fun removeDevice(deviceId: String): OrbitResult<Unit>
    suspend fun triggerDiscovery(): OrbitResult<Unit>
}
