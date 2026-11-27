package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository

/**
 * UseCase for terminating active ORBIT host connection.
 */
class DisconnectDeviceUseCase(
    private val connectionRepository: ConnectionRepository
) {
    suspend fun execute(): OrbitResult<Unit> {
        return connectionRepository.disconnect()
    }
}
