package com.pratyush.orbitmobile.data.local.database.dao

import com.pratyush.orbitmobile.data.local.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object contract for device persistence.
 */
interface DeviceDao {
    fun getAllDevices(): Flow<List<DeviceEntity>>
    fun getPairedDevices(): Flow<List<DeviceEntity>>
    suspend fun insertDevice(device: DeviceEntity)
    suspend fun deleteDevice(deviceId: String)
}
