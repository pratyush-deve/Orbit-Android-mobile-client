package com.pratyush.orbitmobile

import android.app.Application
import com.pratyush.orbitmobile.core.di.AppContainer
import com.pratyush.orbitmobile.core.di.DefaultAppContainer
import com.pratyush.orbitmobile.util.LoggingUtils

/**
 * Application entry point for ORBIT Mobile.
 * Initializes application-scoped dependency injection container.
 */
class OrbitMobileApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        LoggingUtils.i(TAG, "Initializing ORBIT Mobile application foundation...")
        appContainer = DefaultAppContainer(applicationContext)
        try {
            appContainer.lanReceiver.start()
        } catch (e: Exception) {
            LoggingUtils.w(TAG, "Failed to start LAN receiver: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "OrbitMobileApp"
    }
}
