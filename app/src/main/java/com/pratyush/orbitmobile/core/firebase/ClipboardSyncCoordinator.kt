package com.pratyush.orbitmobile.core.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pratyush.orbitmobile.MainActivity
import com.pratyush.orbitmobile.R
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.local.preferences.PreferencesManager
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.EffectiveConnectionState
import com.pratyush.orbitmobile.domain.model.resolveEffectiveConnectionState
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class PendingClipboardEvent(
    val eventId: String,
    val content: String,
    val origin: String,
    val issuedAtMs: Long
)

/**
 * Lifecycle-aware coordinator for bidirectional clipboard synchronization across
 * LAN (primary) and Firebase (remote fallback), respecting Android background restrictions.
 */
class ClipboardSyncCoordinator(
    private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val preferencesManager: PreferencesManager,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val firebasePresenceManager: FirebasePresenceManager,
    private val remoteLinkStore: RemoteLinkStore,
    private val clientDeviceId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val replayCache: PersistentClipboardReplayCache = PersistentClipboardReplayCache(
        persistenceFile = File(context.filesDir, "orbit_clipboard_replay_cache.json")
    ),
    private val loopGuard: ClipboardLoopGuard = ClipboardLoopGuard(
        persistenceFile = File(context.filesDir, "orbit_clipboard_loop_guard.json")
    ),
    private val pendingClipboardStore: PendingClipboardStore? = null,
    private val clipboardApplier: ((String) -> Boolean)? = null,
    private val clipboardTextReader: (() -> String?)? = null,
    private val onListenerStateChanged: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ClipboardCoordinator"
        private const val CHANNEL_ID = "orbit_clipboard_ready"
        private const val NOTIFICATION_ID = 4301
        const val ACTION_APPLY_PENDING_CLIPBOARD = "com.pratyush.orbitmobile.ACTION_APPLY_PENDING_CLIPBOARD"
    }

    private val _activeTransport = MutableStateFlow("DISCONNECTED")
    val activeTransport: StateFlow<String> = _activeTransport.asStateFlow()

    private val _isSyncEnabled = MutableStateFlow(false)
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val _isAppForeground = MutableStateFlow(false)
    val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()

    private val _hasWindowFocus = MutableStateFlow(false)
    val hasWindowFocus: StateFlow<Boolean> = _hasWindowFocus.asStateFlow()

    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    private val _pendingClipboard = MutableStateFlow<PendingClipboardEvent?>(null)
    val pendingClipboard: StateFlow<PendingClipboardEvent?> = _pendingClipboard.asStateFlow()

    private val eventMutex = Mutex()
    private val listenerLock = Any()
    private var unsubscribeFirebase: (() -> Unit)? = null
    private var lastEffectiveState: EffectiveConnectionState = EffectiveConnectionState.Disconnected
    private var isListenerRegistered = false
    private var focusReadCompletedInSession = false

    private val primaryClipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (_isSyncEnabled.value && _isAppForeground.value && lastEffectiveState.isConnected) {
            scope.launch {
                val text = readSystemClipboardText() ?: return@launch
                handleOutgoingClipboardText(text)
            }
        }
    }

    init {
        pendingClipboardStore?.load()?.let { restored ->
            _pendingClipboard.value = restored
        }
        startObserving()
    }

    private fun startObserving() {
        scope.launch {
            combine(
                connectionRepository.connectionState,
                firebasePresenceManager.peerPresence,
                preferencesManager.appConfig.map { it.isClipboardSyncEnabled }.distinctUntilChanged()
            ) { lanState, peerPresence, syncEnabled ->
                Triple(lanState, peerPresence, syncEnabled)
            }.collect { (lanState, peerPresence, syncEnabled) ->
                _isSyncEnabled.value = syncEnabled
                val hasLink = remoteLinkStore.loadLink() != null
                val effective = resolveEffectiveConnectionState(
                    lanState = lanState,
                    peerPresence = peerPresence,
                    hasRemoteLink = hasLink
                )
                lastEffectiveState = effective

                _activeTransport.value = when (effective) {
                    is EffectiveConnectionState.ConnectedLan -> "LAN"
                    is EffectiveConnectionState.ConnectedRemote -> "REMOTE"
                    else -> "DISCONNECTED"
                }

                if (syncEnabled && effective !is EffectiveConnectionState.Disconnected && hasLink) {
                    ensureFirebaseClipboardSubscription()
                } else {
                    stopFirebaseClipboardSubscription()
                }

                if (!syncEnabled) {
                    _pendingClipboard.value = null
                    pendingClipboardStore?.clear()
                    cancelPendingNotification()
                }
                reconcileMonitoringState()
            }
        }
    }

    /**
     * Idempotently reconciles primary-clip listener registration from
     * (syncEnabled && effectiveConnection.isConnected && isAppForeground).
     */
    fun reconcileMonitoringState() {
        val shouldMonitor = _isSyncEnabled.value && lastEffectiveState.isConnected && _isAppForeground.value
        synchronized(listenerLock) {
            if (shouldMonitor && !isListenerRegistered) {
                try {
                    if (onListenerStateChanged != null) {
                        onListenerStateChanged.invoke(true)
                    } else {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.removePrimaryClipChangedListener(primaryClipListener)
                        cm?.addPrimaryClipChangedListener(primaryClipListener)
                    }
                    isListenerRegistered = true
                    _isMonitoringActive.value = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register clipboard listener: ${e.message}")
                }
            } else if (!shouldMonitor && isListenerRegistered) {
                try {
                    if (onListenerStateChanged != null) {
                        onListenerStateChanged.invoke(false)
                    } else {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.removePrimaryClipChangedListener(primaryClipListener)
                    }
                } catch (_: Exception) {
                } finally {
                    isListenerRegistered = false
                    _isMonitoringActive.value = false
                }
            }
        }
    }

    private fun ensureFirebaseClipboardSubscription() {
        if (unsubscribeFirebase != null) return
        unsubscribeFirebase = firebaseRemoteClient.observeClipboardEvents(
            onEvent = { envelope ->
                scope.launch {
                    handleIncomingRemoteEnvelope(envelope)
                }
            },
            onExpiredOrOwn = { expiredId ->
                scope.launch {
                    firebaseRemoteClient.deleteClipboardEvent(expiredId)
                }
            }
        )
    }

    private fun stopFirebaseClipboardSubscription() {
        unsubscribeFirebase?.invoke()
        unsubscribeFirebase = null
    }

    fun setAppForeground(isForeground: Boolean) {
        _isAppForeground.value = isForeground
        if (!isForeground) {
            _hasWindowFocus.value = false
            focusReadCompletedInSession = false
        } else if (_isSyncEnabled.value && _pendingClipboard.value != null) {
            applyPendingClipboard()
        }
        reconcileMonitoringState()
    }

    /**
     * Triggered by MainActivity.onWindowFocusChanged(hasFocus).
     * Performs a single focus-gated clipboard check per foreground session (Android 10+ compliant).
     */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        _hasWindowFocus.value = hasFocus
        if (!hasFocus || !_isAppForeground.value || !_isSyncEnabled.value || !lastEffectiveState.isConnected) {
            return
        }
        if (focusReadCompletedInSession) {
            return
        }
        focusReadCompletedInSession = true
        scope.launch {
            val text = readSystemClipboardText()
            if (!text.isNullOrBlank()) {
                handleOutgoingClipboardText(text)
            }
        }
    }

    private fun readSystemClipboardText(): String? {
        clipboardTextReader?.let { return it() }
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0).text?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "System clipboard read restricted: ${e.message}")
            null
        }
    }

    /**
     * Handles an outgoing plain-text clipboard copy from Android using LAN primary or Firebase fallback.
     */
    suspend fun handleOutgoingClipboardText(text: String): OrbitResult<Unit> {
        if (!_isSyncEnabled.value) {
            return OrbitResult.Error(IllegalStateException("Clipboard sync is disabled"))
        }
        if (!ClipboardCrypto.isValidPlaintextSize(text)) {
            return OrbitResult.Error(IllegalArgumentException("Clipboard text empty or exceeds 64 KB"))
        }
        if (loopGuard.shouldSuppressOutgoing(text)) {
            return OrbitResult.Success(Unit)
        }

        val currentState = lastEffectiveState
        val isUrl = text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)

        val result = when {
            currentState is EffectiveConnectionState.ConnectedLan ||
                connectionRepository.connectionState.value is ConnectionState.Connected -> {
                connectionRepository.sendClipboard(
                    type = if (isUrl) "Url" else "Text",
                    textContent = text,
                    urlContent = if (isUrl) text else null,
                    imageBase64 = null,
                    imageFileName = null
                )
            }
            currentState is EffectiveConnectionState.ConnectedRemote -> {
                val link = remoteLinkStore.loadLink()
                    ?: return OrbitResult.Error(IllegalStateException("No remote link configured"))
                val envelope = ClipboardCrypto.encryptAndSign(
                    sharedSecretBase64 = link.sharedSecret,
                    linkId = link.linkId,
                    senderDeviceId = clientDeviceId,
                    targetDeviceId = link.peerDeviceId,
                    plaintext = text
                )
                firebaseRemoteClient.sendClipboardEnvelope(envelope)
            }
            else -> OrbitResult.Error(IllegalStateException("No active LAN or Remote connection"))
        }

        if (result is OrbitResult.Success) {
            loopGuard.recordOutgoingSent(text)
        }
        return result
    }

    /**
     * Validates, decrypts, and applies/stages an incoming encrypted Firebase clipboard event.
     */
    suspend fun handleIncomingRemoteEnvelope(envelope: ClipboardEventEnvelope): Boolean = eventMutex.withLock {
        if (!_isSyncEnabled.value) {
            Log.d(TAG, "Ignored remote clipboard event id=${envelope.eventId}: sync disabled")
            return false
        }

        val link = remoteLinkStore.loadLink() ?: return false
        val decryptResult = ClipboardCrypto.verifyAndDecrypt(
            envelope = envelope,
            sharedSecretBase64 = link.sharedSecret,
            expectedLinkId = link.linkId,
            expectedLocalDeviceId = clientDeviceId,
            expectedPeerDeviceId = link.peerDeviceId,
            replayCache = replayCache
        )

        if (!decryptResult.success || decryptResult.plaintext.isNullOrEmpty()) {
            Log.w(TAG, "Rejected remote clipboard event id=${envelope.eventId}: error=${decryptResult.error}")
            firebaseRemoteClient.acknowledgeClipboardEvent(envelope.eventId, "Rejected")
            return false
        }

        val plaintext = decryptResult.plaintext
        val byteCount = plaintext.toByteArray(Charsets.UTF_8).size
        if (!loopGuard.tryBeginRemoteApply(envelope.eventId, plaintext)) {
            Log.d(TAG, "Suppressed duplicate clipboard event id=${envelope.eventId}, bytes=$byteCount")
            firebaseRemoteClient.acknowledgeClipboardEvent(envelope.eventId, "Applied")
            return true
        }

        val isUrl = plaintext.startsWith("http://", ignoreCase = true) || plaintext.startsWith("https://", ignoreCase = true)
        connectionRepository.receiveIncomingClipboardFromWindows(
            id = envelope.eventId,
            type = if (isUrl) "Url" else "Text",
            content = plaintext,
            timestamp = envelope.issuedAtMs,
            origin = "Windows (Remote)"
        )

        val applied = if (_isAppForeground.value) {
            applyToSystemClipboard(plaintext)
        } else {
            false
        }

        if (!applied) {
            val pending = PendingClipboardEvent(
                eventId = envelope.eventId,
                content = plaintext,
                origin = "Windows",
                issuedAtMs = envelope.issuedAtMs
            )
            val persisted = pendingClipboardStore?.save(pending) ?: true
            if (!persisted) {
                Log.w(TAG, "Failed to persist staged clipboard event id=${envelope.eventId}; deferring ack")
                return false
            }
            _pendingClipboard.value = pending
            if (pendingClipboardStore?.wasNotified(envelope.eventId) != true) {
                postPendingClipboardNotification()
                pendingClipboardStore?.markNotified(envelope.eventId)
            }
            firebaseRemoteClient.acknowledgeClipboardEvent(envelope.eventId, "Staged")
            Log.i(TAG, "Staged remote clipboard event id=${envelope.eventId}, bytes=$byteCount (backgrounded)")
        } else {
            pendingClipboardStore?.clear()
            _pendingClipboard.value = null
            firebaseRemoteClient.acknowledgeClipboardEvent(envelope.eventId, "Applied")
            Log.i(TAG, "Applied remote clipboard event id=${envelope.eventId}, bytes=$byteCount, transport=REMOTE")
        }

        return true
    }

    /**
     * Applies any staged background clipboard event (on explicit user tap or resume).
     */
    fun applyPendingClipboard(): Boolean {
        val pending = _pendingClipboard.value ?: pendingClipboardStore?.load() ?: return false
        loopGuard.tryBeginRemoteApply(pending.eventId, pending.content)
        val ok = applyToSystemClipboard(pending.content)
        if (ok) {
            _pendingClipboard.value = null
            pendingClipboardStore?.clear()
            cancelPendingNotification()
            scope.launch {
                firebaseRemoteClient.acknowledgeClipboardEvent(pending.eventId, "Applied")
            }
            Log.i(TAG, "Applied staged clipboard event id=${pending.eventId}, bytes=${pending.content.toByteArray(Charsets.UTF_8).size}")
        }
        return ok
    }

    fun dismissPendingClipboard() {
        _pendingClipboard.value = null
        pendingClipboardStore?.clear()
        cancelPendingNotification()
    }

    private fun applyToSystemClipboard(text: String): Boolean {
        clipboardApplier?.let { return it(text) }
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            cm.setPrimaryClip(ClipData.newPlainText("ORBIT", text))
            true
        } catch (e: Exception) {
            Log.w(TAG, "System clipboard write restricted: ${e.message}")
            false
        }
    }

    private fun postPendingClipboardNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, "Clipboard Sync", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(ch)
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_APPLY_PENDING_CLIPBOARD
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Clipboard ready from PC")
                .setContentText("Tap to copy")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    private fun cancelPendingNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
