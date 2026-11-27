package com.pratyush.orbitmobile.networking.discovery

import com.pratyush.orbitmobile.domain.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Interface contract for local network discovery of ORBIT Windows desktop peers (e.g., mDNS / UDP broadcast).
 */
interface NetworkDiscoveryService {
    val discoveredDevices: Flow<List<Device>>
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
}
