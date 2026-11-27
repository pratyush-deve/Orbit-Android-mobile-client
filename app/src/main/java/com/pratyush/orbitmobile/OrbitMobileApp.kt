package com.pratyush.orbitmobile

import android.app.Application
import com.pratyush.orbitmobile.core.di.AppContainer
import com.pratyush.orbitmobile.core.di.DefaultAppContainer
import com.pratyush.orbitmobile.util.LoggingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point for ORBIT Mobile.
 * Initializes application-scoped dependency injection container.
 */
class OrbitMobileApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        appScope.launch {
            appContainer.firebaseIdentityManager.ensureSignedIn()
            appContainer.firebasePresenceManager.start()
        }
    }

    companion object {
        private const val TAG = "OrbitMobileApp"
    }
}
