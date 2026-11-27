package com.pratyush.orbitmobile.networking.transport

import com.pratyush.orbitmobile.core.common.OrbitResult
import kotlinx.coroutines.flow.Flow

/**
 * Transport abstraction allowing LAN and remote transports to coexist
 * without altering upper layers or presentation screens.
 */
interface TransportChannel {
    val transportType: TransportType
    val incomingBytes: Flow<ByteArray>
    val isConnected: Boolean

    suspend fun connect(target: String, port: Int): OrbitResult<Unit>
    suspend fun send(data: ByteArray): OrbitResult<Unit>
    suspend fun close(): OrbitResult<Unit>
}
