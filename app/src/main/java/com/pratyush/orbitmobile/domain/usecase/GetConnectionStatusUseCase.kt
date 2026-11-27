package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * UseCase for observing current connection status with the ORBIT host.
 */
class GetConnectionStatusUseCase(
    private val connectionRepository: ConnectionRepository
) {
    fun execute(): StateFlow<ConnectionState> = connectionRepository.connectionState
}
