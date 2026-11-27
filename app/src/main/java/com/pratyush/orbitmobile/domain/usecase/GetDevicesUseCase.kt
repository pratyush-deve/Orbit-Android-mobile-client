package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow

/**
 * UseCase for observing available and paired devices.
 */
class GetDevicesUseCase(
    private val deviceRepository: DeviceRepository
) {
    fun executeDiscovered(): Flow<List<Device>> = deviceRepository.getDiscoveredDevices()
    fun executePaired(): Flow<List<Device>> = deviceRepository.getPairedDevices()
}
