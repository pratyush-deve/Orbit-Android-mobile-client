package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.repository.SettingsRepository

/**
 * UseCase for mutating application configuration.
 */
class SaveSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend fun updateAutoConnect(enabled: Boolean): OrbitResult<Unit> =
        settingsRepository.updateAutoConnect(enabled)

    suspend fun updateEndpoint(host: String, port: Int): OrbitResult<Unit> =
        settingsRepository.updateDefaultEndpoint(host, port)

    suspend fun updateHapticFeedback(enabled: Boolean): OrbitResult<Unit> =
        settingsRepository.updateHapticFeedback(enabled)

    suspend fun updateKeepConnectedInBackground(enabled: Boolean): OrbitResult<Unit> =
        settingsRepository.updateKeepConnectedInBackground(enabled)

    suspend fun updateTrackpadSensitivity(sensitivity: Float): OrbitResult<Unit> =
        settingsRepository.updateTrackpadSensitivity(sensitivity)

    suspend fun updateTrackpadTapToClick(enabled: Boolean): OrbitResult<Unit> =
        settingsRepository.updateTrackpadTapToClick(enabled)

    suspend fun updateTrackpadTwoFingerTapRightClick(enabled: Boolean): OrbitResult<Unit> =
        settingsRepository.updateTrackpadTwoFingerTapRightClick(enabled)
}
