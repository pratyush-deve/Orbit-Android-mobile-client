package com.pratyush.orbitmobile.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pratyush.orbitmobile.presentation.screens.about.AboutViewModel
import com.pratyush.orbitmobile.presentation.screens.connection.ConnectionViewModel
import com.pratyush.orbitmobile.presentation.screens.devices.DevicesViewModel
import com.pratyush.orbitmobile.presentation.screens.home.HomeViewModel
import com.pratyush.orbitmobile.presentation.screens.settings.SettingsViewModel

/**
 * Centrally managed ViewModelFactory injecting use cases via constructor injection.
 */
class ViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    getConnectionStatusUseCase = appContainer.getConnectionStatusUseCase,
                    connectDeviceUseCase = appContainer.connectDeviceUseCase,
                    disconnectDeviceUseCase = appContainer.disconnectDeviceUseCase,
                    connectionRepository = appContainer.connectionRepository,
                    preferencesManager = appContainer.preferencesManager,
                    context = appContainer.applicationContext,
                    controlsRepository = appContainer.controlsRepository,
                    firebasePresenceManager = appContainer.firebasePresenceManager,
                    remoteLinkStore = appContainer.remoteLinkStore,
                    clipboardSyncCoordinator = appContainer.clipboardSyncCoordinator
                ) as T
            }
            modelClass.isAssignableFrom(ConnectionViewModel::class.java) -> {
                ConnectionViewModel(
                    getConnectionStatusUseCase = appContainer.getConnectionStatusUseCase,
                    getDevicesUseCase = appContainer.getDevicesUseCase,
                    connectDeviceUseCase = appContainer.connectDeviceUseCase,
                    disconnectDeviceUseCase = appContainer.disconnectDeviceUseCase,
                    networkMonitor = appContainer.networkMonitor
                ) as T
            }
            modelClass.isAssignableFrom(DevicesViewModel::class.java) -> {
                DevicesViewModel(
                    getDevicesUseCase = appContainer.getDevicesUseCase,
                    connectDeviceUseCase = appContainer.connectDeviceUseCase,
                    removeDeviceUseCase = appContainer.removeDeviceUseCase
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    getSettingsUseCase = appContainer.getSettingsUseCase,
                    saveSettingsUseCase = appContainer.saveSettingsUseCase
                ) as T
            }
            modelClass.isAssignableFrom(AboutViewModel::class.java) -> {
                AboutViewModel() as T
            }
            modelClass.isAssignableFrom(com.pratyush.orbitmobile.presentation.screens.controls.ControlsViewModel::class.java) -> {
                com.pratyush.orbitmobile.presentation.screens.controls.ControlsViewModel(
                    controlsRepository = appContainer.controlsRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.pratyush.orbitmobile.presentation.screens.trackpad.TrackpadViewModel::class.java) -> {
                com.pratyush.orbitmobile.presentation.screens.trackpad.TrackpadViewModel(
                    trackpadRepository = appContainer.trackpadRepository,
                    preferencesManager = appContainer.preferencesManager,
                    connectionRepository = appContainer.connectionRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.pratyush.orbitmobile.presentation.screens.systemmonitor.SystemMonitorViewModel::class.java) -> {
                com.pratyush.orbitmobile.presentation.screens.systemmonitor.SystemMonitorViewModel(
                    systemMonitorRepository = appContainer.systemMonitorRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.pratyush.orbitmobile.presentation.screens.ai.AiChatViewModel::class.java) -> {
                com.pratyush.orbitmobile.presentation.screens.ai.AiChatViewModel(
                    aiChatRepository = appContainer.aiChatRepository
                ) as T
            }
            modelClass.isAssignableFrom(com.pratyush.orbitmobile.presentation.screens.filesystem.send.SendFilesViewModel::class.java) -> {
                com.pratyush.orbitmobile.presentation.screens.filesystem.send.SendFilesViewModel(
                    fileTransferRepository = appContainer.fileTransferRepository
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
