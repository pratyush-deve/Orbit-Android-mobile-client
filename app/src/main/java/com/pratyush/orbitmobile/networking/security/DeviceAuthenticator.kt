package com.pratyush.orbitmobile.networking.security

import com.pratyush.orbitmobile.core.common.OrbitResult

/**
 * Interface contract for cryptographically verifying and authenticating desktop peers.
 */
interface DeviceAuthenticator {
    suspend fun verifyHandshake(
        deviceId: String,
        challenge: ByteArray,
        signature: ByteArray
    ): OrbitResult<Boolean>

    suspend fun signPayload(payload: ByteArray): OrbitResult<ByteArray>
}
