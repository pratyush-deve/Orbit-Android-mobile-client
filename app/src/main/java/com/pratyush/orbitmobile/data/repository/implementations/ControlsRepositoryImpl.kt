package com.pratyush.orbitmobile.data.repository.implementations

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.firebase.FirebasePresenceManager
import com.pratyush.orbitmobile.core.firebase.FirebaseRemoteClient
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.domain.model.BluetoothDevice
import com.pratyush.orbitmobile.domain.model.BrightnessControlStatus
import com.pratyush.orbitmobile.domain.model.CameraControlStatus
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.ControlEvent
import com.pratyush.orbitmobile.domain.model.LaptopControlsStatus
import com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus
import com.pratyush.orbitmobile.domain.model.VolumeControlStatus
import com.pratyush.orbitmobile.domain.model.WifiNetworkItem
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.ControlsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControlsRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val remoteDataSource: RemoteDataSource,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val firebaseRemoteClient: FirebaseRemoteClient? = null,
    private val firebasePresenceManager: FirebasePresenceManager? = null
) : ControlsRepository {

    private val _controlsStatus = MutableStateFlow(LaptopControlsStatus())
    override val controlsStatus: StateFlow<LaptopControlsStatus> = _controlsStatus.asStateFlow()

    private val _wifiNetworks = MutableStateFlow<List<WifiNetworkItem>>(emptyList())
    override val wifiNetworks: StateFlow<List<WifiNetworkItem>> = _wifiNetworks.asStateFlow()

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val bluetoothDevices: StateFlow<List<BluetoothDevice>> = _bluetoothDevices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override val isConnected: StateFlow<Boolean> = if (firebasePresenceManager != null) {
        combine(connectionRepository.connectionState, firebasePresenceManager.peerPresence) { lanState, peer ->
            lanState is ConnectionState.Connected || (peer?.online == true && firebaseRemoteClient?.isRemoteAvailable == true)
        }.stateIn(scope, SharingStarted.Eagerly, connectionRepository.connectionState.value is ConnectionState.Connected)
    } else {
        combine(connectionRepository.connectionState, MutableStateFlow<Any?>(null)) { lanState, _ ->
            lanState is ConnectionState.Connected
        }.stateIn(scope, SharingStarted.Eagerly, connectionRepository.connectionState.value is ConnectionState.Connected)
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var sseJob: Job? = null
    private var remoteControlsDetach: (() -> Unit)? = null

    init {
        scope.launch {
            if (firebasePresenceManager != null) {
                combine(connectionRepository.connectionState, firebasePresenceManager.peerPresence) { lanState, peer ->
                    Pair(lanState is ConnectionState.Connected, peer?.online == true)
                }.collect { (lanConnected, remoteOnline) ->
                    if (lanConnected) {
                        stopRemoteControlsListener()
                        startSseListener()
                        launch { refreshControlsStatus() }
                    } else if (remoteOnline && firebaseRemoteClient?.isRemoteAvailable == true) {
                        stopSseListener()
                        startRemoteControlsListener()
                    } else {
                        stopSseListener()
                        stopRemoteControlsListener()
                    }
                }
            } else {
                connectionRepository.connectionState.collect { state ->
                    if (state is ConnectionState.Connected) {
                        startSseListener()
                        launch { refreshControlsStatus() }
                    } else {
                        stopSseListener()
                    }
                }
            }
        }
    }

    private fun startRemoteControlsListener() {
        if (remoteControlsDetach != null) return
        remoteControlsDetach = firebaseRemoteClient?.observeControls { status ->
            _controlsStatus.value = status
        }
    }

    private fun stopRemoteControlsListener() {
        remoteControlsDetach?.invoke()
        remoteControlsDetach = null
    }

    private fun startSseListener() {
        stopSseListener()
        sseJob = scope.launch {
            while (isActive && connectionRepository.connectionState.value is ConnectionState.Connected) {
                val (host, token) = getHostAndToken() ?: break
                val port = connectionRepository.activePort
                try {
                    Log.i("ControlsRepo", "Subscribing to controls SSE stream at $host:$port")
                    remoteDataSource.listenToControlEvents(host, port, token).collect { event ->
                    when (event) {
                        is ControlEvent.Snapshot -> {
                            _controlsStatus.value = event.status
                        }
                        is ControlEvent.VolumeUpdate -> {
                            _controlsStatus.update { current ->
                                current.copy(
                                    volume = current.volume.copy(
                                        percentage = event.percentage,
                                        isMuted = event.isMuted
                                    )
                                )
                            }
                        }
                        is ControlEvent.BrightnessUpdate -> {
                            _controlsStatus.update { current ->
                                current.copy(
                                    brightness = current.brightness.copy(
                                        percentage = event.percentage,
                                        isAvailable = event.isAvailable,
                                        statusLabel = event.statusLabel
                                    )
                                )
                            }
                        }
                        is ControlEvent.GenericControlUpdate -> {
                            when (event.type) {
                                "wifi" -> {
                                    _controlsStatus.update { current ->
                                        current.copy(
                                            wifi = current.wifi.copy(
                                                isAvailable = event.isAvailable,
                                                isEnabled = event.isEnabled,
                                                detail = event.detail
                                            )
                                        )
                                    }
                                }
                                "bluetooth" -> {
                                    _controlsStatus.update { current ->
                                        current.copy(
                                            bluetooth = current.bluetooth.copy(
                                                isAvailable = event.isAvailable,
                                                isEnabled = event.isEnabled,
                                                statusLabel = event.statusLabel,
                                                detail = event.detail
                                            )
                                        )
                                    }
                                }
                                "microphone" -> {
                                    _controlsStatus.update { current ->
                                        current.copy(
                                            microphone = current.microphone.copy(
                                                isAvailable = event.isAvailable,
                                                isEnabled = event.isEnabled,
                                                isMuted = !event.isEnabled,
                                                statusLabel = event.statusLabel
                                            )
                                        )
                                    }
                                }
                                "camera" -> {
                                    _controlsStatus.update { current ->
                                        current.copy(
                                            camera = current.camera.copy(
                                                isAvailable = event.isAvailable,
                                                isEnabled = event.isEnabled,
                                                statusLabel = event.statusLabel,
                                                detail = event.detail
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                } catch (e: Exception) {
                    Log.d("ControlsRepo", "SSE stream ended: ${e.message}")
                }
                if (isActive && connectionRepository.connectionState.value is ConnectionState.Connected) {
                    Log.d("ControlsRepo", "SSE stream dropped while Connected; re-subscribing in 3s...")
                    delay(3000L)
                }
            }
        }
    }

    private fun stopSseListener() {
        sseJob?.cancel()
        sseJob = null
    }

    private fun getHostAndToken(): Pair<String, String>? {
        if (connectionRepository.connectionState.value !is ConnectionState.Connected) return null
        val host = connectionRepository.activeHost ?: return null
        val token = connectionRepository.activeAuthToken ?: "anonymous"
        return Pair(host, token)
    }

    private fun shouldUseRemoteFallback(): Boolean {
        return connectionRepository.connectionState.value !is ConnectionState.Connected &&
            firebaseRemoteClient?.isRemoteAvailable == true
    }

    override suspend fun refreshControlsStatus(): OrbitResult<LaptopControlsStatus> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                _isLoading.value = true
                val res = firebaseRemoteClient!!.sendCommand("controls.refresh")
                _isLoading.value = false
                return when (res) {
                    is OrbitResult.Success -> OrbitResult.Success(_controlsStatus.value)
                    is OrbitResult.Error -> {
                        _errorMessage.value = res.exception.message
                        OrbitResult.Error(res.exception)
                    }
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        _isLoading.value = true
        _errorMessage.value = null

        val result = remoteDataSource.fetchControlsStatus(host, connectionRepository.activePort, token)
        _isLoading.value = false

        if (result is OrbitResult.Success) {
            _controlsStatus.value = result.data
        } else if (result is OrbitResult.Error) {
            _errorMessage.value = result.exception.message
            Log.w("ControlsRepo", "Failed to refresh controls: ${result.exception.message}")
        }

        return result
    }

    override suspend fun refreshWifiNetworks(): OrbitResult<List<WifiNetworkItem>> {
        val (host, token) = getHostAndToken() ?: return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        _isLoading.value = true

        val result = remoteDataSource.fetchWifiNetworks(host, connectionRepository.activePort, token)
        _isLoading.value = false

        if (result is OrbitResult.Success) {
            _wifiNetworks.value = result.data
        }
        return result
    }

    override suspend fun connectWifi(ssid: String): OrbitResult<Boolean> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("ssid" to ssid)
                return when (val r = firebaseRemoteClient.sendCommand("controls.wifi.connect", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(true)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.connectWifi(host, connectionRepository.activePort, token, ssid)
        if (result is OrbitResult.Success) {
            refreshControlsStatus()
            refreshWifiNetworks()
        }
        return result
    }

    override suspend fun disconnectWifi(): OrbitResult<Boolean> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                return when (val r = firebaseRemoteClient!!.sendCommand("controls.wifi.disconnect")) {
                    is OrbitResult.Success -> OrbitResult.Success(true)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.disconnectWifi(host, connectionRepository.activePort, token)
        if (result is OrbitResult.Success) {
            refreshControlsStatus()
            refreshWifiNetworks()
        }
        return result
    }

    override suspend fun toggleWifiPower(enabled: Boolean?): OrbitResult<Boolean> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("enabled" to enabled)
                return when (val r = firebaseRemoteClient.sendCommand("controls.wifi.power", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(true)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.toggleWifiPower(host, connectionRepository.activePort, token, enabled)
        if (result is OrbitResult.Success) {
            refreshControlsStatus()
        }
        return result
    }

    override suspend fun toggleBluetoothPower(enabled: Boolean?): OrbitResult<Boolean> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("enabled" to enabled)
                return when (val r = firebaseRemoteClient.sendCommand("controls.bluetooth.power", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(true)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.toggleBluetoothPower(host, connectionRepository.activePort, token, enabled)
        if (result is OrbitResult.Success) {
            refreshControlsStatus()
            refreshBluetoothDevices()
        }
        return result
    }

    override suspend fun refreshBluetoothDevices(): OrbitResult<List<BluetoothDevice>> {
        val (host, token) = getHostAndToken() ?: return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        _isLoading.value = true

        val result = remoteDataSource.fetchBluetoothDevices(host, connectionRepository.activePort, token)
        _isLoading.value = false

        if (result is OrbitResult.Success) {
            _bluetoothDevices.value = result.data
        }
        return result
    }

    override suspend fun connectBluetoothDevice(deviceId: String): OrbitResult<Boolean> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("deviceId" to deviceId)
                return when (val r = firebaseRemoteClient.sendCommand("controls.bluetooth.connect", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(true)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.connectBluetoothDevice(host, connectionRepository.activePort, token, deviceId)
        if (result is OrbitResult.Success) {
            refreshBluetoothDevices()
            refreshControlsStatus()
        }
        return result
    }

    override suspend fun disconnectBluetoothDevice(deviceId: String?): OrbitResult<Boolean> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("deviceId" to deviceId)
                return when (val r = firebaseRemoteClient.sendCommand("controls.bluetooth.disconnect", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(true)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.disconnectBluetoothDevice(host, connectionRepository.activePort, token, deviceId)
        if (result is OrbitResult.Success) {
            refreshBluetoothDevices()
            refreshControlsStatus()
        }
        return result
    }

    override suspend fun toggleMicrophone(mute: Boolean?): OrbitResult<MicrophoneControlStatus> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("mute" to mute)
                return when (val r = firebaseRemoteClient.sendCommand("controls.microphone.toggle", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(_controlsStatus.value.microphone)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.toggleMicrophone(host, connectionRepository.activePort, token, mute)
        if (result is OrbitResult.Success) {
            _controlsStatus.update { it.copy(microphone = result.data) }
        }
        return result
    }

    override suspend fun toggleCamera(enabled: Boolean?): OrbitResult<CameraControlStatus> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("enabled" to enabled)
                return when (val r = firebaseRemoteClient.sendCommand("controls.camera.toggle", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(_controlsStatus.value.camera)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.toggleCamera(host, connectionRepository.activePort, token, enabled)
        if (result is OrbitResult.Success) {
            _controlsStatus.update { it.copy(camera = result.data) }
        }
        return result
    }

    override suspend fun setVolume(percentage: Int): OrbitResult<VolumeControlStatus> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("percentage" to percentage)
                return when (val r = firebaseRemoteClient.sendCommand("controls.volume.set", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(_controlsStatus.value.volume.copy(percentage = percentage))
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.setVolume(host, connectionRepository.activePort, token, percentage)
        if (result is OrbitResult.Success) {
            _controlsStatus.update { it.copy(volume = result.data) }
        }
        return result
    }

    override suspend fun toggleVolumeMute(mute: Boolean?): OrbitResult<VolumeControlStatus> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("mute" to mute)
                return when (val r = firebaseRemoteClient.sendCommand("controls.volume.mute", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(_controlsStatus.value.volume)
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.toggleVolumeMute(host, connectionRepository.activePort, token, mute)
        if (result is OrbitResult.Success) {
            _controlsStatus.update { it.copy(volume = result.data) }
        }
        return result
    }

    override suspend fun setBrightness(percentage: Int): OrbitResult<BrightnessControlStatus> {
        val lan = getHostAndToken()
        if (lan == null) {
            if (shouldUseRemoteFallback()) {
                val payload = firebaseRemoteClient!!.jsonPayload("percentage" to percentage)
                return when (val r = firebaseRemoteClient.sendCommand("controls.brightness.set", payload)) {
                    is OrbitResult.Success -> OrbitResult.Success(_controlsStatus.value.brightness.copy(percentage = percentage, statusLabel = "$percentage%"))
                    is OrbitResult.Error -> OrbitResult.Error(r.exception)
                    else -> OrbitResult.Loading
                }
            }
            return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        }
        val (host, token) = lan
        val result = remoteDataSource.setBrightness(host, connectionRepository.activePort, token, percentage)
        if (result is OrbitResult.Success) {
            _controlsStatus.update { it.copy(brightness = result.data) }
        }
        return result
    }

    override suspend fun refreshBrightness(): OrbitResult<BrightnessControlStatus> {
        val (host, token) = getHostAndToken() ?: return OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        val result = remoteDataSource.fetchBrightness(host, connectionRepository.activePort, token)
        if (result is OrbitResult.Success) {
            _controlsStatus.update { it.copy(brightness = result.data) }
        }
        return result
    }
}
