package com.pratyush.orbitmobile.presentation.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.firebase.FirebasePresenceManager
import com.pratyush.orbitmobile.core.firebase.RemoteLinkStore
import com.pratyush.orbitmobile.core.platform.TelemetryCollector
import com.pratyush.orbitmobile.data.local.preferences.PreferencesManager
import com.pratyush.orbitmobile.domain.model.ClipboardItem
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.EffectiveConnectionState
import com.pratyush.orbitmobile.domain.model.resolveEffectiveConnectionState
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.ControlsRepository
import com.pratyush.orbitmobile.domain.usecase.ConnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.DisconnectDeviceUseCase
import com.pratyush.orbitmobile.domain.usecase.GetConnectionStatusUseCase
import com.pratyush.orbitmobile.presentation.screens.home.actions.HomeAction
import com.pratyush.orbitmobile.services.OrbitBackgroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the connected Home screen.
 * Streams real live telemetry (Battery, Wi-Fi, BT) and clipboard sync to ORBIT Windows.
 * Implements strict Android 10+ foreground-only clipboard monitoring with echo prevention.
 */
class HomeViewModel(
    private val getConnectionStatusUseCase: GetConnectionStatusUseCase,
    private val connectDeviceUseCase: ConnectDeviceUseCase,
    private val disconnectDeviceUseCase: DisconnectDeviceUseCase,
    private val connectionRepository: ConnectionRepository,
    private val preferencesManager: PreferencesManager,
    private val context: Context,
    private val controlsRepository: ControlsRepository? = null,
    private val firebasePresenceManager: FirebasePresenceManager? = null,
    private val remoteLinkStore: RemoteLinkStore? = null,
    private val clipboardSyncCoordinator: com.pratyush.orbitmobile.core.firebase.ClipboardSyncCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    private val loopGuard by lazy {
        com.pratyush.orbitmobile.core.firebase.ClipboardLoopGuard(
            persistenceFile = java.io.File(context.filesDir, "orbit_clipboard_loop_guard.json")
        )
    }

    // Echo prevention: track what we've sent and what was copied from Windows
    @Volatile
    private var lastSentClipboardText: String? = null
    @Volatile
    private var lastReceivedFromWindowsText: String? = null

    private val primaryClipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (_uiState.value.isClipboardSyncActive) {
            syncClipboardAuto()
        }
    }

    init {
        loadPreferences()
        observeConnectionStatus()
        observeClipboardHistory()
        observePendingRemoteClipboard()
    }

    private fun observePendingRemoteClipboard() {
        clipboardSyncCoordinator?.pendingClipboard
            ?.onEach { pending ->
                _uiState.update { it.copy(hasPendingRemoteClipboard = pending != null) }
            }
            ?.launchIn(viewModelScope)
    }

    fun applyPendingRemoteClipboard() {
        if (clipboardSyncCoordinator?.applyPendingClipboard() == true) {
            _uiState.update { it.copy(lastActionFeedback = "Copied PC clipboard to device!") }
        }
    }

    private fun loadPreferences() {
        preferencesManager.appConfig
            .onEach { config ->
                _uiState.update { it.copy(isClipboardSyncActive = config.isClipboardSyncEnabled) }
                reconcileLegacyClipboardListener()
            }
            .launchIn(viewModelScope)
    }

    private fun reconcileLegacyClipboardListener() {
        // When ClipboardSyncCoordinator is present, it owns the global foreground listener.
        if (clipboardSyncCoordinator != null) {
            unregisterClipboardListener()
            return
        }
        if (_uiState.value.isClipboardSyncActive && _uiState.value.effectiveConnectionState.isConnected) {
            registerClipboardListener()
        } else {
            unregisterClipboardListener()
        }
    }

    private fun observeConnectionStatus() {
        val peerPresenceFlow = firebasePresenceManager?.peerPresence ?: MutableStateFlow(null)
        val reconnectingFlow = _uiState.map { it.isReconnecting }.distinctUntilChanged()

        combine(
            getConnectionStatusUseCase.execute(),
            peerPresenceFlow,
            reconnectingFlow
        ) { status, peerPresence, isReconnecting ->
            Triple(status, peerPresence, isReconnecting)
        }
            .onEach { (status, peerPresence, isReconnecting) ->
                val hasRemoteLink = remoteLinkStore?.loadLink() != null
                _uiState.update { current ->
                    val newDeviceName = when {
                        status is ConnectionState.Connected -> status.deviceName
                        status is ConnectionState.Connecting -> status.deviceName
                        status is ConnectionState.TemporarilyUnavailable -> status.deviceName
                        peerPresence?.online == true && !peerPresence.isStale -> peerPresence.deviceName
                        else -> current.lastKnownDeviceName
                    }
                    val resolvedDeviceName = if (newDeviceName.isNotBlank()) newDeviceName else current.lastKnownDeviceName
                    val effective = resolveEffectiveConnectionState(
                        lanState = status,
                        peerPresence = peerPresence,
                        hasRemoteLink = hasRemoteLink,
                        isReconnecting = isReconnecting,
                        fallbackDeviceName = resolvedDeviceName
                    )
                    current.copy(
                        connectionState = status,
                        effectiveConnectionState = effective,
                        lastKnownDeviceName = resolvedDeviceName
                    )
                }
                reconcileLegacyClipboardListener()
                if (_uiState.value.effectiveConnectionState.isConnected && status is ConnectionState.Connected) {
                    viewModelScope.launch {
                        connectionRepository.updateClipboardSyncState(_uiState.value.isClipboardSyncActive)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeClipboardHistory() {
        connectionRepository.clipboardHistory
            .onEach { history ->
                _uiState.update { it.copy(clipboardHistory = history) }
            }
            .launchIn(viewModelScope)
    }

    fun registerClipboardListener() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(primaryClipListener)
            clipboardManager?.addPrimaryClipChangedListener(primaryClipListener)
        } catch (e: Exception) {
            Log.w("HomeVM", "Could not register clipboard listener: ${e.message}")
        }
    }

    fun unregisterClipboardListener() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(primaryClipListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.ReconnectRequested -> {
                if (_uiState.value.isReconnecting) return
                viewModelScope.launch {
                    val targetName = _uiState.value.lastKnownDeviceName
                    Log.i("HomeVM", "Manual reconnect requested for $targetName")
                    _uiState.update {
                        it.copy(
                            isReconnecting = true,
                            lastActionFeedback = "Reconnecting to $targetName..."
                        )
                    }
                    try {
                        preferencesManager.setExplicitlyDisconnected(false)
                        val result = connectionRepository.attemptReconnect()
                        if (result is OrbitResult.Success) {
                            OrbitBackgroundService.requestImmediateTelemetry(context)
                            controlsRepository?.refreshControlsStatus()
                            connectionRepository.refreshClipboardHistoryFromPc()
                            _uiState.update {
                                it.copy(
                                    isReconnecting = false,
                                    lastActionFeedback = "Connected to $targetName"
                                )
                            }
                        } else if (result is OrbitResult.Error) {
                            val errMsg = result.exception.message ?: "Reconnection failed"
                            Log.w("HomeVM", "Manual reconnect failed: $errMsg")
                            _uiState.update {
                                it.copy(
                                    isReconnecting = false,
                                    lastActionFeedback = errMsg
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isReconnecting = false) }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeVM", "Manual reconnect exception: ${e.message}", e)
                        _uiState.update {
                            it.copy(
                                isReconnecting = false,
                                lastActionFeedback = e.message ?: "Reconnection failed"
                            )
                        }
                    }
                }
            }
            is HomeAction.DisconnectRequested -> {
                viewModelScope.launch {
                    // 1. Immediately update UI state to Disconnected
                    _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
                    // 2. Mark explicitly disconnected in preferences so next launch does not auto-reconnect
                    preferencesManager.setExplicitlyDisconnected(true)
                    // 3. Disconnect active session on both Android and Windows
                    disconnectDeviceUseCase.execute()
                }
            }
            is HomeAction.AppResumed -> {
                // Foreground lifecycle refresh: trigger fresh telemetry snapshot, controls, and clipboard
                val effectiveState = _uiState.value.effectiveConnectionState
                val isLan = _uiState.value.connectionState is ConnectionState.Connected
                if (isLan) {
                    OrbitBackgroundService.requestImmediateTelemetry(context)
                    viewModelScope.launch {
                        controlsRepository?.refreshControlsStatus()
                    }
                }
                if (_uiState.value.isClipboardSyncActive && effectiveState.isConnected) {
                    reconcileLegacyClipboardListener()
                    if (clipboardSyncCoordinator == null) {
                        checkAndSyncNewPhoneClipboardOnly()
                    }
                    if (isLan) {
                        viewModelScope.launch {
                            connectionRepository.refreshClipboardHistoryFromPc()
                        }
                    }
                }
            }
            is HomeAction.ToggleClipboardSync -> {
                val enabled = action.enabled
                _uiState.update { it.copy(isClipboardSyncActive = enabled) }
                viewModelScope.launch {
                    preferencesManager.setClipboardSync(enabled)
                    connectionRepository.updateClipboardSyncState(enabled)
                }
                reconcileLegacyClipboardListener()
                if (enabled && clipboardSyncCoordinator == null) {
                    syncClipboardAuto()
                }
            }
            is HomeAction.SyncClipboardNow -> {
                triggerManualSync()
            }
            is HomeAction.ToggleMuteMic -> {
                _uiState.update { it.copy(isMicMuted = !it.isMicMuted) }
            }
            is HomeAction.TogglePlayPause -> {
                _uiState.update { it.copy(isMediaPlaying = !it.isMediaPlaying) }
            }
            is HomeAction.AdjustVolume -> {
                _uiState.update {
                    val newVolume = (it.currentVolumePercent + action.deltaPercent).coerceIn(0, 100)
                    it.copy(currentVolumePercent = newVolume)
                }
            }
            is HomeAction.OpenAiAssistant -> {
                _uiState.update { it.copy(lastActionFeedback = "Launching Desktop AI Assistant...") }
            }
            is HomeAction.OpenRemoteControls -> {
                _uiState.update { it.copy(lastActionFeedback = "Opening Full Remote Controls...") }
            }
            is HomeAction.OpenTrackpad -> {
                _uiState.update { it.copy(lastActionFeedback = "Opening Remote Trackpad...") }
            }
            is HomeAction.OpenFileAccess -> {
                _uiState.update { it.copy(lastActionFeedback = "Opening Send Files to Laptop...") }
            }
            is HomeAction.OpenSystemMonitor -> {
                _uiState.update { it.copy(lastActionFeedback = "Opening Desktop System Monitor...") }
            }
        }
    }

    /**
     * Inspects the current system clipboard on app launch/resume.
     * Transmits ONLY if the content is genuinely new and not already present in local history or previously synced.
     */
    private fun checkAndSyncNewPhoneClipboardOnly() {
        viewModelScope.launch {
            try {
                val cm = clipboardManager ?: return@launch
                val clip = cm.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch
                val item = clip.getItemAt(0)
                val text = item.text?.toString() ?: item.uri?.toString()
                if (text.isNullOrBlank()) return@launch

                _uiState.update { it.copy(currentClipText = text) }

                if (!_uiState.value.isClipboardSyncActive) return@launch
                if (!com.pratyush.orbitmobile.core.firebase.ClipboardCrypto.isValidPlaintextSize(text)) return@launch
                if (loopGuard.shouldSuppressOutgoing(text)) return@launch

                // Check against last received from Windows
                if (text == lastReceivedFromWindowsText) {
                    Log.d("HomeVM", "Startup check: matches last received from Windows, skipping")
                    return@launch
                }

                // Check against last sent
                if (text == lastSentClipboardText) {
                    Log.d("HomeVM", "Startup check: already sent, skipping")
                    return@launch
                }

                // Genuinely new item copied while outside the app: synchronize it
                Log.i("HomeVM", "Genuinely new clipboard content detected on resume, broadcasting to Windows")
                val isUrl = text.startsWith("http://") || text.startsWith("https://")
                val result = connectionRepository.sendClipboard(
                    type = if (isUrl) "Url" else "Text",
                    textContent = text,
                    urlContent = if (isUrl) text else null,
                    imageBase64 = null,
                    imageFileName = null
                )
                if (result is OrbitResult.Success) {
                    loopGuard.recordOutgoingSent(text)
                    lastSentClipboardText = text
                    _uiState.update { it.copy(lastActionFeedback = "Copied to ORBIT Windows clipboard!") }
                }
            } catch (e: Exception) {
                Log.w("HomeVM", "Failed during checkAndSyncNewPhoneClipboardOnly: ${e.message}")
            }
        }
    }

    /**
     * Public manual trigger for clipboard synchronization.
     */
    fun syncClipboardNow() {
        triggerManualSync()
    }

    /**
     * Manually triggers an immediate clipboard synchronization and reconciles history with Windows.
     * Operates independently of the automatic synchronization toggle.
     * Prevents duplicate simultaneous requests, provides loading status, and reports success/failure.
     */
    fun triggerManualSync() {
        if (_uiState.value.isManualSyncing) return

        viewModelScope.launch {
            val effective = _uiState.value.effectiveConnectionState
            if (!effective.isConnected) {
                _uiState.update {
                    it.copy(
                        manualSyncStatus = ManualSyncStatus.Failure("Windows PC is disconnected"),
                        lastActionFeedback = "Cannot sync: Windows PC is not connected"
                    )
                }
                delay(2500)
                _uiState.update {
                    if (it.manualSyncStatus !is ManualSyncStatus.InProgress) {
                        it.copy(manualSyncStatus = ManualSyncStatus.Idle)
                    } else it
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isManualSyncing = true,
                    manualSyncStatus = ManualSyncStatus.InProgress,
                    lastActionFeedback = "Synchronizing with Windows..."
                )
            }

            try {
                // 1. Reconcile available history with Windows PC when on LAN
                if (_uiState.value.connectionState is ConnectionState.Connected) {
                    connectionRepository.refreshClipboardHistoryFromPc()
                }

                // 2. Read and send current eligible phone clipboard via ClipboardSyncCoordinator
                var sendFailure: String? = null
                val cm = clipboardManager
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item.text?.toString() ?: item.uri?.toString()
                    _uiState.update { it.copy(currentClipText = text) }

                    if (!text.isNullOrBlank()) {
                        if (!com.pratyush.orbitmobile.core.firebase.ClipboardCrypto.isValidPlaintextSize(text)) {
                            sendFailure = "Clipboard exceeds 64 KB limit"
                        } else if (!loopGuard.shouldSuppressOutgoing(text) && text != lastReceivedFromWindowsText) {
                            val sendResult = if (clipboardSyncCoordinator != null) {
                                clipboardSyncCoordinator.handleOutgoingClipboardText(text)
                            } else {
                                val isUrl = text.startsWith("http://") || text.startsWith("https://")
                                connectionRepository.sendClipboard(
                                    type = if (isUrl) "Url" else "Text",
                                    textContent = text,
                                    urlContent = if (isUrl) text else null,
                                    imageBase64 = null,
                                    imageFileName = null
                                )
                            }
                            if (sendResult is OrbitResult.Success) {
                                loopGuard.recordOutgoingSent(text)
                                lastSentClipboardText = text
                            } else if (sendResult is OrbitResult.Error) {
                                sendFailure = sendResult.exception.message ?: "Sync failed"
                            }
                        }
                    }
                }

                if (sendFailure != null) {
                    _uiState.update {
                        it.copy(
                            isManualSyncing = false,
                            manualSyncStatus = ManualSyncStatus.Failure(sendFailure),
                            lastActionFeedback = "Sync failed: $sendFailure"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isManualSyncing = false,
                            manualSyncStatus = ManualSyncStatus.Success,
                            lastActionFeedback = "Clipboard synchronized successfully!"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("HomeVM", "Manual sync error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isManualSyncing = false,
                        manualSyncStatus = ManualSyncStatus.Failure(e.message ?: "Sync failed"),
                        lastActionFeedback = "Sync failed: ${e.message}"
                    )
                }
            }

            delay(2500)
            _uiState.update {
                if (it.manualSyncStatus !is ManualSyncStatus.InProgress) {
                    it.copy(manualSyncStatus = ManualSyncStatus.Idle)
                } else it
            }
        }
    }

    /**
     * Background handler for automatic clipboard change events.
     */
    private fun syncClipboardAuto() {
        viewModelScope.launch {
            try {
                val cm = clipboardManager ?: return@launch
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item.text?.toString() ?: item.uri?.toString()
                    _uiState.update { it.copy(currentClipText = text) }

                    if (!text.isNullOrBlank()) {
                        if (!_uiState.value.isClipboardSyncActive) return@launch
                        if (!com.pratyush.orbitmobile.core.firebase.ClipboardCrypto.isValidPlaintextSize(text)) return@launch
                        if (loopGuard.shouldSuppressOutgoing(text)) return@launch
                        if (text == lastReceivedFromWindowsText) return@launch
                        if (text == lastSentClipboardText) return@launch

                        val hash = com.pratyush.orbitmobile.domain.model.ClipboardItem.computeHash(text)
                        val topItem = _uiState.value.clipboardHistory.firstOrNull()
                        if (topItem != null && topItem.contentHash == hash && (System.currentTimeMillis() - topItem.timestamp) < 3000) {
                            lastSentClipboardText = text
                            return@launch
                        }

                        val result = if (clipboardSyncCoordinator != null) {
                            clipboardSyncCoordinator.handleOutgoingClipboardText(text)
                        } else {
                            val isUrl = text.startsWith("http://") || text.startsWith("https://")
                            connectionRepository.sendClipboard(
                                type = if (isUrl) "Url" else "Text",
                                textContent = text,
                                urlContent = if (isUrl) text else null,
                                imageBase64 = null,
                                imageFileName = null
                            )
                        }
                        if (result is OrbitResult.Success) {
                            loopGuard.recordOutgoingSent(text)
                            lastSentClipboardText = text
                            _uiState.update { it.copy(lastActionFeedback = "Copied to ORBIT Windows clipboard!") }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("HomeVM", "Auto clipboard sync failed: ${e.message}")
            }
        }
    }

    /**
     * Copies a string to the local Android clipboard without triggering an echo back to Windows.
     */
    fun copyToLocalClipboard(text: String) {
        try {
            loopGuard.tryBeginRemoteApply("local-apply-${System.currentTimeMillis()}", text)
            lastReceivedFromWindowsText = text
            val clipData = ClipData.newPlainText("ORBIT", text)
            clipboardManager?.setPrimaryClip(clipData)
            _uiState.update {
                it.copy(
                    currentClipText = text,
                    lastActionFeedback = "Copied to device clipboard!"
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(lastActionFeedback = "Failed to copy: ${e.message}") }
        }
    }

    fun clearClipboardHistory() {
        viewModelScope.launch {
            connectionRepository.clearClipboardHistory()
            _uiState.update { it.copy(lastActionFeedback = "Clipboard history cleared") }
        }
    }

    fun getCurrentClipContent(): String? {
        return try {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString() ?: clip.getItemAt(0).uri?.toString()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterClipboardListener()
    }
}
