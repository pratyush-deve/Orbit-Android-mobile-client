package com.pratyush.orbitmobile.core.firebase

import android.os.Build
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.pratyush.orbitmobile.core.common.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class RemotePeerPresence(
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val online: Boolean,
    val lastSeenMs: Long,
    val sessionId: String,
    val isStale: Boolean
)

/**
 * Manages Firebase Realtime Database presence for Android and observes the paired Windows peer.
 * Uses `.info/connected` and `onDisconnect()` to ensure stale presence is cleaned up automatically.
 */
class FirebasePresenceManager(
    private val identityManager: FirebaseIdentityManager,
    private val linkStore: RemoteLinkStore,
    private val clientDeviceId: String,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(RTDB_URL),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "OrbitFirebasePresence"
        const val RTDB_URL = "https://orbit-4c44d-default-rtdb.asia-southeast1.firebasedatabase.app"
        private const val STALE_THRESHOLD_MS = 75_000L
        private const val KEEPALIVE_INTERVAL_MS = 30_000L
    }

    private val _peerPresence = MutableStateFlow<RemotePeerPresence?>(null)
    val peerPresence: StateFlow<RemotePeerPresence?> = _peerPresence.asStateFlow()

    private val _isFirebaseConnected = MutableStateFlow(false)
    val isFirebaseConnected: StateFlow<Boolean> = _isFirebaseConnected.asStateFlow()

    private var activeLinkId: String? = null
    private var activeSessionId: String = UUID.randomUUID().toString()
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var devicesRef: DatabaseReference? = null
    private var devicesListener: ValueEventListener? = null
    private var keepaliveJob: Job? = null
    private var startJob: Job? = null

    fun start() {
        val link = linkStore.loadLink() ?: run {
            Log.d(TAG, "No remote link configured; skipping Firebase presence startup.")
            return
        }

        if (activeLinkId == link.linkId && connectedListener != null && devicesListener != null) {
            return
        }

        stop()
        activeLinkId = link.linkId
        activeSessionId = UUID.randomUUID().toString()

        startJob = scope.launch {
            val uid = identityManager.ensureSignedIn()
            if (uid.isNullOrBlank()) {
                Log.w(TAG, "Cannot start Firebase presence: anonymous authentication unavailable.")
                return@launch
            }
            attachListeners(link.linkId, uid)
        }
    }

    fun refreshNow() {
        val linkId = activeLinkId ?: linkStore.loadLink()?.linkId ?: return
        val uid = identityManager.currentUid ?: return
        if (connectedListener == null || devicesListener == null) {
            start()
            return
        }
        if (_isFirebaseConnected.value) {
            writePresenceOnline(linkId, uid)
        }
    }

    fun stop(markOffline: Boolean = false) {
        startJob?.cancel()
        startJob = null
        keepaliveJob?.cancel()
        keepaliveJob = null

        val linkId = activeLinkId
        if (markOffline && linkId != null) {
            markPresenceOffline(linkId)
        }

        connectedListener?.let { connectedRef?.removeEventListener(it) }
        connectedListener = null
        connectedRef = null

        devicesListener?.let { devicesRef?.removeEventListener(it) }
        devicesListener = null
        devicesRef = null

        activeLinkId = null
        _isFirebaseConnected.value = false
    }

    private fun attachListeners(linkId: String, uid: String) {
        Log.i(TAG, "Initializing Firebase presence listeners for linkId=$linkId, uid=$uid")

        val infoConnected = database.getReference(".info/connected")
        val connListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                _isFirebaseConnected.value = connected
                if (connected) {
                    Log.i(TAG, "Firebase RTDB connected (.info/connected=true); registering onDisconnect & online presence.")
                    registerOnDisconnectAndOnline(linkId, uid)
                } else {
                    Log.w(TAG, "Firebase RTDB disconnected (.info/connected=false).")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Firebase .info/connected listener cancelled: ${error.message}")
            }
        }
        infoConnected.addValueEventListener(connListener)
        connectedRef = infoConnected
        connectedListener = connListener

        val devRef = database.getReference("links").child(linkId).child("devices")
        val devListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var windowsPeer: RemotePeerPresence? = null
                val now = System.currentTimeMillis()
                for (child in snapshot.children) {
                    val devId = child.key ?: continue
                    if (devId == clientDeviceId) continue
                    val p = child.child("presence")
                    if (!p.exists()) continue

                    val role = p.child("role").getValue(String::class.java) ?: "windows"
                    val online = p.child("online").getValue(Boolean::class.java) == true
                    val lastSeen = p.child("lastSeen").getValue(Long::class.java) ?: 0L
                    val deviceName = p.child("deviceName").getValue(String::class.java) ?: "ORBIT Windows PC"
                    val sessionId = p.child("sessionId").getValue(String::class.java) ?: ""
                    val isStale = !online || (lastSeen > 0L && (now - lastSeen) > STALE_THRESHOLD_MS)

                    windowsPeer = RemotePeerPresence(
                        deviceId = devId,
                        deviceName = deviceName,
                        role = role,
                        online = online && !isStale,
                        lastSeenMs = lastSeen,
                        sessionId = sessionId,
                        isStale = isStale
                    )
                    break
                }

                val prev = _peerPresence.value
                if (prev?.online != windowsPeer?.online) {
                    Log.i(
                        TAG,
                        "Remote peer presence transition: ${prev?.online} -> ${windowsPeer?.online} " +
                            "(device=${windowsPeer?.deviceName ?: "none"}, stale=${windowsPeer?.isStale})"
                    )
                }
                _peerPresence.value = windowsPeer
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Authorization/read error on /links/$linkId/devices: ${error.message}")
            }
        }
        devRef.addValueEventListener(devListener)
        devicesRef = devRef
        devicesListener = devListener

        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (isActive) {
                delay(KEEPALIVE_INTERVAL_MS)
                if (_isFirebaseConnected.value && activeLinkId == linkId) {
                    writePresenceOnline(linkId, uid)
                }
            }
        }
    }

    private fun registerOnDisconnectAndOnline(linkId: String, uid: String) {
        val presenceRef = database.getReference("links")
            .child(linkId)
            .child("devices")
            .child(clientDeviceId)
            .child("presence")

        presenceRef.onDisconnect().updateChildren(
            mapOf(
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
        ).addOnFailureListener { e ->
            Log.w(TAG, "Failed to register onDisconnect handler: ${e.message}")
        }

        writePresenceOnline(linkId, uid)
    }

    private fun writePresenceOnline(linkId: String, uid: String) {
        val presenceRef = database.getReference("links")
            .child(linkId)
            .child("devices")
            .child(clientDeviceId)
            .child("presence")

        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val payload = mapOf(
            "online" to true,
            "role" to "android",
            "ownerUid" to uid,
            "deviceName" to deviceName,
            "sessionId" to activeSessionId,
            "appVersion" to Constants.APP_VERSION,
            "lastSeen" to ServerValue.TIMESTAMP
        )

        presenceRef.setValue(payload)
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to update Android presence: ${e.message}")
            }
    }

    private fun markPresenceOffline(linkId: String) {
        try {
            val presenceRef = database.getReference("links")
                .child(linkId)
                .child("devices")
                .child(clientDeviceId)
                .child("presence")
            presenceRef.updateChildren(
                mapOf(
                    "online" to false,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark Android presence offline: ${e.message}")
        }
    }
}
