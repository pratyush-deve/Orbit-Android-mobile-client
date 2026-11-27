package com.pratyush.orbitmobile.data.mapper

import com.pratyush.orbitmobile.data.local.database.entity.DeviceEntity
import com.pratyush.orbitmobile.domain.model.Device

/**
 * Mapper extension functions between data entities and domain models.
 */
fun DeviceEntity.toDomain(isOnline: Boolean = false): Device {
    return Device(
        id = id,
        name = name,
        ipAddress = ipAddress,
        port = port,
        isPaired = isPaired,
        lastSeenEpochMs = lastSeenEpochMs,
        osInfo = osInfo,
        isOnline = isOnline
    )
}

fun Device.toEntity(): DeviceEntity {
    return DeviceEntity(
        id = id,
        name = name,
        ipAddress = ipAddress,
        port = port,
        isPaired = isPaired,
        lastSeenEpochMs = lastSeenEpochMs,
        osInfo = osInfo
    )
}
