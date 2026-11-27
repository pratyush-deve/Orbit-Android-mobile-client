package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.repository.DeviceRepository

/**
 * UseCase for removing a previously paired desktop device.
 */
class RemoveDeviceUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend fun execute(deviceId: String): OrbitResult<Unit> {
        return deviceRepository.removeDevice(deviceId)
    }
}
