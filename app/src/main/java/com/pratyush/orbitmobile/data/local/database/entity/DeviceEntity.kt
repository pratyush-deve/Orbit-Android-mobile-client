package com.pratyush.orbitmobile.data.local.database.entity

/**
 * Data layer entity representing a persisted desktop peer.
 * Ready for Room annotations when persistence is initialized.
 */
data class DeviceEntity(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val isPaired: Boolean,
    val lastSeenEpochMs: Long,
    val osInfo: String
)
