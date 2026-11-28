package com.pratyush.orbitmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitTheme
import com.pratyush.orbitmobile.core.navigation.AppNavigation
import com.pratyush.orbitmobile.services.OrbitBackgroundService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Main activity hosting ORBIT Mobile Jetpack Compose UI.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_ROUTE = "extra_open_route"
    }

    private val pendingNavigationRoute = mutableStateOf<String?>(null)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission granted or declined
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()
        pendingNavigationRoute.value = intent?.getStringExtra(EXTRA_OPEN_ROUTE)

        val appContainer = (application as OrbitMobileApp).appContainer

        lifecycleScope.launch {
            val appConfig = appContainer.preferencesManager.appConfig.firstOrNull()
            val paired = appContainer.deviceRepository.getPairedDevices().firstOrNull()?.firstOrNull()
            if (paired != null && appConfig?.isExplicitlyDisconnected != true && appConfig?.keepConnectedInBackground != false) {
                OrbitBackgroundService.start(this@MainActivity, paired.name)
            }
        }

        setContent {
            OrbitTheme {
                AppNavigation(
                    appContainer = appContainer,
                    pendingRoute = pendingNavigationRoute.value,
                    onPendingRouteConsumed = { pendingNavigationRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = intent.getStringExtra(EXTRA_OPEN_ROUTE)
        if (!route.isNullOrBlank()) {
            pendingNavigationRoute.value = route
        }
    }

    override fun onStart() {
        super.onStart()
        (application as? OrbitMobileApp)?.appContainer?.firebasePresenceManager?.refreshNow()
    }

    override fun onResume() {
        super.onResume()
        val coordinator = (application as? OrbitMobileApp)?.appContainer?.clipboardSyncCoordinator
        coordinator?.setAppForeground(true)
        if (intent?.action == com.pratyush.orbitmobile.core.firebase.ClipboardSyncCoordinator.ACTION_APPLY_PENDING_CLIPBOARD) {
            coordinator?.applyPendingClipboard()
            intent?.action = null
        }
    }

    override fun onPause() {
        (application as? OrbitMobileApp)?.appContainer?.clipboardSyncCoordinator?.setAppForeground(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        (application as? OrbitMobileApp)?.appContainer?.clipboardSyncCoordinator?.onWindowFocusChanged(hasFocus)
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
