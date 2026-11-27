package com.pratyush.orbitmobile.networking.connection

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface contract orchestrating discovery, handshakes, transport lifecycle, and heartbeats.
 */
interface ConnectionManager {
    val activeState: StateFlow<ConnectionState>
    suspend fun initiateConnection(device: Device): OrbitResult<Unit>
    suspend fun terminateConnection(): OrbitResult<Unit>
}
