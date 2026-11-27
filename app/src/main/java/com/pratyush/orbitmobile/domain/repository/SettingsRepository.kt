package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for managing application preferences and configurations.
 */
interface SettingsRepository {
    fun getSettings(): Flow<AppConfig>
    suspend fun updateAutoConnect(enabled: Boolean): OrbitResult<Unit>
    suspend fun updateDefaultEndpoint(host: String, port: Int): OrbitResult<Unit>
    suspend fun updateHapticFeedback(enabled: Boolean): OrbitResult<Unit>
    suspend fun updateKeepConnectedInBackground(enabled: Boolean): OrbitResult<Unit>
    suspend fun updateTrackpadSensitivity(sensitivity: Float): OrbitResult<Unit>
    suspend fun updateTrackpadTapToClick(enabled: Boolean): OrbitResult<Unit>
    suspend fun updateTrackpadTwoFingerTapRightClick(enabled: Boolean): OrbitResult<Unit>
}
