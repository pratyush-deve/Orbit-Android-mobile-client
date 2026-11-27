package com.pratyush.orbitmobile.data.repository.implementations

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.local.preferences.PreferencesManager
import com.pratyush.orbitmobile.domain.model.AppConfig
import com.pratyush.orbitmobile.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Settings repository implementation backed by [PreferencesManager].
 */
class SettingsRepositoryImpl(
    private val preferencesManager: PreferencesManager
) : SettingsRepository {

    override fun getSettings(): Flow<AppConfig> = preferencesManager.appConfig

    override suspend fun updateAutoConnect(enabled: Boolean): OrbitResult<Unit> {
        return try {
            preferencesManager.setAutoConnect(enabled)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateDefaultEndpoint(host: String, port: Int): OrbitResult<Unit> {
        return try {
            preferencesManager.setDefaultEndpoint(host, port)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateHapticFeedback(enabled: Boolean): OrbitResult<Unit> {
        return try {
            preferencesManager.setHapticFeedback(enabled)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateKeepConnectedInBackground(enabled: Boolean): OrbitResult<Unit> {
        return try {
            preferencesManager.setKeepConnectedInBackground(enabled)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateTrackpadSensitivity(sensitivity: Float): OrbitResult<Unit> {
        return try {
            preferencesManager.setTrackpadSensitivity(sensitivity)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateTrackpadTapToClick(enabled: Boolean): OrbitResult<Unit> {
        return try {
            preferencesManager.setTrackpadTapToClick(enabled)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun updateTrackpadTwoFingerTapRightClick(enabled: Boolean): OrbitResult<Unit> {
        return try {
            preferencesManager.setTrackpadTwoFingerTapRightClick(enabled)
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }
}
