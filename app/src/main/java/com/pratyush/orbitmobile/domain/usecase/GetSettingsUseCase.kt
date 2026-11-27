package com.pratyush.orbitmobile.domain.usecase

import com.pratyush.orbitmobile.domain.model.AppConfig
import com.pratyush.orbitmobile.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * UseCase for retrieving reactive application configuration stream.
 */
class GetSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    fun execute(): Flow<AppConfig> = settingsRepository.getSettings()
}
