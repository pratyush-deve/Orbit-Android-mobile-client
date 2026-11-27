package com.pratyush.orbitmobile.core.common

/**
 * Application-wide constants for ORBIT Mobile.
 */
object Constants {
    const val APP_NAME = "ORBIT Mobile"
    const val APP_VERSION = "1.0.0"

    object Preferences {
        const val PREFS_DATASTORE_NAME = "orbit_mobile_preferences"
        const val KEY_AUTO_CONNECT = "pref_auto_connect"
        const val KEY_DESKTOP_HOST = "pref_desktop_host"
        const val KEY_DESKTOP_PORT = "pref_desktop_port"
        const val KEY_THEME_MODE = "pref_theme_mode"
        const val KEY_HAPTIC_FEEDBACK = "pref_haptic_feedback"
        const val KEY_CLIPBOARD_SYNC = "pref_clipboard_sync"
        const val KEY_EXPLICITLY_DISCONNECTED = "pref_explicitly_disconnected"
        const val KEY_LAST_CONNECTED_DEVICE_ID = "pref_last_connected_device_id"
        const val KEY_KEEP_CONNECTED_IN_BACKGROUND = "pref_keep_connected_in_background"
        const val KEY_TRACKPAD_SENSITIVITY = "pref_trackpad_sensitivity"
        const val KEY_TRACKPAD_TAP_TO_CLICK = "pref_trackpad_tap_to_click"
        const val KEY_TRACKPAD_TWO_FINGER_TAP = "pref_trackpad_two_finger_tap"
    }

    object NetworkDefaults {
        const val DEFAULT_PORT = 8089
        const val DISCOVERY_TIMEOUT_MS = 5000L
        const val HEARTBEAT_INTERVAL_MS = 3000L
        const val CONNECTION_TIMEOUT_MS = 10000L
        const val SERVICE_TYPE_MDNS = "_orbit._tcp"
    }

    object Security {
        const val KEYSTORE_ALIAS = "OrbitMobileMasterKey"
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}
