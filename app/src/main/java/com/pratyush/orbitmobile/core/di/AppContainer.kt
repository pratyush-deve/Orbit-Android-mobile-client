package com.pratyush.orbitmobile.core.di

import android.content.Context
import com.pratyush.orbitmobile.core.platform.DefaultKeystoreManager
import com.pratyush.orbitmobile.core.platform.DefaultNetworkMonitor
import com.pratyush.orbitmobile.core.platform.KeystoreManager
import com.pratyush.orbitmobile.core.platform.NetworkMonitor
import com.pratyush.orbitmobile.data.local.preferences.DefaultPreferencesManager
import com.pratyush.orbitmobile.data.local.preferences.PreferencesManager
import com.pratyush.orbitmobile.data.remote.DefaultRemoteDataSource
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.data.repository.implementations.ConnectionRepositoryImpl
import com.pratyush.orbitmobile.data.repository.implementations.DeviceRepositoryImpl
import com.pratyush.orbitmobile.data.repository.implementations.SettingsRepositoryImpl
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.DeviceRepository
import com.pratyush.orbitmobile.domain.repository.SettingsRepository
import com.pratyush.orbitmobile.domain.usecase.ConnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.DisconnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.GetConnectionStatusUseCase
import com.pratyush.orbitmobile.domain.usecase.GetDevicesUseCase
import com.pratyush.orbitmobile.domain.usecase.GetSettingsUseCase
import com.pratyush.orbitmobile.domain.usecase.RemoveDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.SaveSettingsUseCase

/**
 * Dependency injection container interface providing application-scoped dependencies.
 */
interface AppContainer {
    val applicationContext: Context
    val preferencesManager: PreferencesManager
    val keystoreManager: KeystoreManager
    val networkMonitor: NetworkMonitor

    val remoteDataSource: RemoteDataSource
    val deviceRepository: DeviceRepository
    val connectionRepository: ConnectionRepository
    val controlsRepository: com.pratyush.orbitmobile.domain.repository.ControlsRepository
    val trackpadRepository: com.pratyush.orbitmobile.domain.repository.TrackpadRepository
    val systemMonitorRepository: com.pratyush.orbitmobile.domain.repository.SystemMonitorRepository
    val aiChatRepository: com.pratyush.orbitmobile.domain.repository.AiChatRepository
    val fileTransferRepository: com.pratyush.orbitmobile.domain.repository.FileTransferRepository
    val settingsRepository: SettingsRepository
    val lanReceiver: com.pratyush.orbitmobile.data.remote.AndroidLanReceiver

    val getDevicesUseCase: GetDevicesUseCase
    val removeDeviceUseCase: RemoveDeviceUseCase
    val getConnectionStatusUseCase: GetConnectionStatusUseCase
    val connectDeviceUseCase: ConnectDeviceUseCase
    val disconnectDeviceUseCase: DisconnectDeviceUseCase
    val getSettingsUseCase: GetSettingsUseCase
    val saveSettingsUseCase: SaveSettingsUseCase
}

/**
 * Default implementation of [AppContainer] instantiated at Application level.
 */
class DefaultAppContainer(private val appContext: Context) : AppContainer {

    override val applicationContext: Context = appContext

    override val preferencesManager: PreferencesManager by lazy {
        DefaultPreferencesManager(appContext)
    }

    override val keystoreManager: KeystoreManager by lazy {
        DefaultKeystoreManager()
    }

    override val networkMonitor: NetworkMonitor by lazy {
        DefaultNetworkMonitor(appContext)
    }

    override val remoteDataSource: RemoteDataSource by lazy {
        DefaultRemoteDataSource()
    }

    override val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl(appContext)
    }

    override val connectionRepository: ConnectionRepository by lazy {
        ConnectionRepositoryImpl(remoteDataSource, deviceRepository, appContext)
    }

    override val controlsRepository: com.pratyush.orbitmobile.domain.repository.ControlsRepository by lazy {
        com.pratyush.orbitmobile.data.repository.implementations.ControlsRepositoryImpl(
            connectionRepository = connectionRepository,
            remoteDataSource = remoteDataSource
        )
    }

    override val trackpadRepository: com.pratyush.orbitmobile.domain.repository.TrackpadRepository by lazy {
        com.pratyush.orbitmobile.data.repository.implementations.TrackpadRepositoryImpl(
            connectionRepository = connectionRepository,
            remoteDataSource = remoteDataSource
        )
    }

    override val systemMonitorRepository: com.pratyush.orbitmobile.domain.repository.SystemMonitorRepository by lazy {
        com.pratyush.orbitmobile.data.repository.implementations.SystemMonitorRepositoryImpl(
            connectionRepository = connectionRepository,
            remoteDataSource = remoteDataSource
        )
    }

    val database: com.pratyush.orbitmobile.data.local.database.OrbitDatabase by lazy {
        com.pratyush.orbitmobile.data.local.database.OrbitDatabaseHelper.getInstance(appContext)
    }

    override val aiChatRepository: com.pratyush.orbitmobile.domain.repository.AiChatRepository by lazy {
        com.pratyush.orbitmobile.data.repository.implementations.AiChatRepositoryImpl(
            connectionRepository = connectionRepository,
            remoteDataSource = remoteDataSource,
            database = database
        )
    }

    override val fileTransferRepository: com.pratyush.orbitmobile.domain.repository.FileTransferRepository by lazy {
        com.pratyush.orbitmobile.data.repository.implementations.FileTransferRepositoryImpl(
            context = appContext,
            connectionRepository = connectionRepository,
            remoteDataSource = remoteDataSource
        )
    }

    override val lanReceiver: com.pratyush.orbitmobile.data.remote.AndroidLanReceiver by lazy {
        com.pratyush.orbitmobile.data.remote.AndroidLanReceiver(connectionRepository)
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(preferencesManager)
    }

    override val getDevicesUseCase: GetDevicesUseCase by lazy {
        GetDevicesUseCase(deviceRepository)
    }

    override val removeDeviceUseCase: RemoveDeviceUseCase by lazy {
        RemoveDeviceUseCase(deviceRepository)
    }

    override val getConnectionStatusUseCase: GetConnectionStatusUseCase by lazy {
        GetConnectionStatusUseCase(connectionRepository)
    }

    override val connectDeviceUseCase: ConnectDeviceUseCase by lazy {
        ConnectDeviceUseCase(connectionRepository)
    }

    override val disconnectDeviceUseCase: DisconnectDeviceUseCase by lazy {
        DisconnectDeviceUseCase(connectionRepository)
    }

    override val getSettingsUseCase: GetSettingsUseCase by lazy {
        GetSettingsUseCase(settingsRepository)
    }

    override val saveSettingsUseCase: SaveSettingsUseCase by lazy {
        SaveSettingsUseCase(settingsRepository)
    }
}
