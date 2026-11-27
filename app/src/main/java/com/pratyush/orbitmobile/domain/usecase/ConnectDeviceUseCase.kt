package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository

/**
 * UseCase for initiating a connection to an ORBIT desktop instance.
 */
class ConnectDeviceUseCase(
    private val connectionRepository: ConnectionRepository
) {
    suspend fun execute(device: Device): OrbitResult<Unit> {
        return connectionRepository.connectToDevice(device)
    }

    suspend fun executeManual(host: String, port: Int): OrbitResult<Unit> {
        return connectionRepository.connectManual(host, port)
    }

    suspend fun executeQrPairing(qrRawPayload: String): OrbitResult<Device> {
        return connectionRepository.pairWithQr(qrRawPayload)
    }
}
