package com.pratyush.orbitmobile.domain.model

/**
 * Domain representation of an ORBIT Desktop peer device.
 */
data class Device(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val isPaired: Boolean = false,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val lastConnectedText: String = "Recent",
    val osInfo: String = "Windows 11 Pro",
    val isOnline: Boolean = false
)
