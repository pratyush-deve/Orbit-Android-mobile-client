package com.pratyush.orbitmobile.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pratyush.orbitmobile.core.common.Constants
import com.pratyush.orbitmobile.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.Preferences.PREFS_DATASTORE_NAME
)

/**
 * Interface contract for managing application preferences.
 */
interface PreferencesManager {
    val appConfig: Flow<AppConfig>
    suspend fun setAutoConnect(enabled: Boolean)
    suspend fun setDefaultEndpoint(host: String, port: Int)
    suspend fun setHapticFeedback(enabled: Boolean)
    suspend fun setClipboardSync(enabled: Boolean)
    suspend fun setExplicitlyDisconnected(disconnected: Boolean)
    suspend fun setLastConnectedDeviceId(deviceId: String)
    suspend fun setKeepConnectedInBackground(enabled: Boolean)
    suspend fun setTrackpadSensitivity(sensitivity: Float)
    suspend fun setTrackpadTapToClick(enabled: Boolean)
    suspend fun setTrackpadTwoFingerTapRightClick(enabled: Boolean)
}

/**
 * Android DataStore implementation of [PreferencesManager].
 */
class DefaultPreferencesManager(
    private val context: Context
) : PreferencesManager {

    private object PreferencesKeys {
        val AUTO_CONNECT = booleanPreferencesKey(Constants.Preferences.KEY_AUTO_CONNECT)
        val DESKTOP_HOST = stringPreferencesKey(Constants.Preferences.KEY_DESKTOP_HOST)
        val DESKTOP_PORT = intPreferencesKey(Constants.Preferences.KEY_DESKTOP_PORT)
        val HAPTIC_FEEDBACK = booleanPreferencesKey(Constants.Preferences.KEY_HAPTIC_FEEDBACK)
        val CLIPBOARD_SYNC = booleanPreferencesKey(Constants.Preferences.KEY_CLIPBOARD_SYNC)
        val EXPLICITLY_DISCONNECTED = booleanPreferencesKey(Constants.Preferences.KEY_EXPLICITLY_DISCONNECTED)
        val LAST_CONNECTED_DEVICE_ID = stringPreferencesKey(Constants.Preferences.KEY_LAST_CONNECTED_DEVICE_ID)
        val KEEP_CONNECTED_IN_BACKGROUND = booleanPreferencesKey(Constants.Preferences.KEY_KEEP_CONNECTED_IN_BACKGROUND)
        val TRACKPAD_SENSITIVITY = floatPreferencesKey(Constants.Preferences.KEY_TRACKPAD_SENSITIVITY)
        val TRACKPAD_TAP_TO_CLICK = booleanPreferencesKey(Constants.Preferences.KEY_TRACKPAD_TAP_TO_CLICK)
        val TRACKPAD_TWO_FINGER_TAP = booleanPreferencesKey(Constants.Preferences.KEY_TRACKPAD_TWO_FINGER_TAP)
    }

    override val appConfig: Flow<AppConfig> = context.dataStore.data.map { preferences ->
        AppConfig(
            autoConnect = preferences[PreferencesKeys.AUTO_CONNECT] ?: true,
            defaultHost = preferences[PreferencesKeys.DESKTOP_HOST] ?: "192.168.1.100",
            defaultPort = preferences[PreferencesKeys.DESKTOP_PORT] ?: Constants.NetworkDefaults.DEFAULT_PORT,
            hapticFeedbackEnabled = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true,
            isPitchBlackTheme = true,
            isClipboardSyncEnabled = preferences[PreferencesKeys.CLIPBOARD_SYNC] ?: false,
            isExplicitlyDisconnected = preferences[PreferencesKeys.EXPLICITLY_DISCONNECTED] ?: false,
            lastConnectedDeviceId = preferences[PreferencesKeys.LAST_CONNECTED_DEVICE_ID] ?: "",
            keepConnectedInBackground = preferences[PreferencesKeys.KEEP_CONNECTED_IN_BACKGROUND] ?: true,
            trackpadSensitivity = preferences[PreferencesKeys.TRACKPAD_SENSITIVITY] ?: 1.0f,
            trackpadTapToClick = preferences[PreferencesKeys.TRACKPAD_TAP_TO_CLICK] ?: true,
            trackpadTwoFingerTapRightClick = preferences[PreferencesKeys.TRACKPAD_TWO_FINGER_TAP] ?: true
        )
    }

    override suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CONNECT] = enabled
        }
    }

    override suspend fun setDefaultEndpoint(host: String, port: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DESKTOP_HOST] = host
            preferences[PreferencesKeys.DESKTOP_PORT] = port
        }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = enabled
        }
    }

    override suspend fun setClipboardSync(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIPBOARD_SYNC] = enabled
        }
    }

    override suspend fun setExplicitlyDisconnected(disconnected: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXPLICITLY_DISCONNECTED] = disconnected
        }
    }

    override suspend fun setLastConnectedDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CONNECTED_DEVICE_ID] = deviceId
        }
    }

    override suspend fun setKeepConnectedInBackground(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_CONNECTED_IN_BACKGROUND] = enabled
        }
    }

    override suspend fun setTrackpadSensitivity(sensitivity: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACKPAD_SENSITIVITY] = sensitivity
        }
    }

    override suspend fun setTrackpadTapToClick(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACKPAD_TAP_TO_CLICK] = enabled
        }
    }

    override suspend fun setTrackpadTwoFingerTapRightClick(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACKPAD_TWO_FINGER_TAP] = enabled
        }
    }
}
