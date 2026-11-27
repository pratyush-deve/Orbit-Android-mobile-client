package com.pratyush.orbitmobile.core.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.BluetoothControlStatus
import com.pratyush.orbitmobile.domain.model.BrightnessControlStatus
import com.pratyush.orbitmobile.domain.model.CameraControlStatus
import com.pratyush.orbitmobile.domain.model.CpuStats
import com.pratyush.orbitmobile.domain.model.GpuStats
import com.pratyush.orbitmobile.domain.model.LaptopControlsStatus
import com.pratyush.orbitmobile.domain.model.MicrophoneControlStatus
import com.pratyush.orbitmobile.domain.model.RamStats
import com.pratyush.orbitmobile.domain.model.SystemTelemetry
import com.pratyush.orbitmobile.domain.model.VolumeControlStatus
import com.pratyush.orbitmobile.domain.model.WifiControlStatus
import com.pratyush.orbitmobile.domain.model.WifiStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/**
 * Firebase RTDB remote transport client for Phase 2:
 * - Signs and dispatches remote commands with HMAC-SHA256 + nonce + expiry
 * - Awaits command acknowledgements and distinguishes delivery vs ack loss
 * - Subscribes to scoped controls and telemetry paths
 */
class FirebaseRemoteClient(
    private val identityManager: FirebaseIdentityManager,
    private val linkStore: RemoteLinkStore,
    private val presenceManager: FirebasePresenceManager,
    private val clientDeviceId: String,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(FirebasePresenceManager.RTDB_URL)
) {
    companion object {
        private const val TAG = "OrbitFirebaseRemote"
        private const val COMMAND_TTL_MS = 30_000L
        private const val ACK_TIMEOUT_MS = 12_000L
    }

    private val secureRandom = SecureRandom()

    val isRemoteAvailable: Boolean
        get() = linkStore.loadLink() != null && presenceManager.peerPresence.value?.online == true

    suspend fun sendCommand(
        commandType: String,
        payloadJson: String = "{}",
        existingCommandId: String? = null
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val link = linkStore.loadLink()
            ?: return@withContext OrbitResult.Error(IllegalStateException("No remote link configured."))

        val uid = identityManager.ensureSignedIn()
            ?: return@withContext OrbitResult.Error(IllegalStateException("Firebase authentication unavailable."))

        val commandId = existingCommandId ?: UUID.randomUUID().toString()
        val nonceBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        val nonce = nonceBytes.joinToString("") { "%02x".format(it) }
        val issuedAtMs = System.currentTimeMillis()
        val expiresAtMs = issuedAtMs + COMMAND_TTL_MS

        val canonical = RemoteCommandCrypto.buildCanonicalMessage(
            commandId = commandId,
            linkId = link.linkId,
            senderDeviceId = clientDeviceId,
            targetDeviceId = link.peerDeviceId,
            commandType = commandType,
            issuedAtMs = issuedAtMs,
            expiresAtMs = expiresAtMs,
            nonce = nonce,
            canonicalPayloadJson = payloadJson
        )
        val signature = RemoteCommandCrypto.sign(link.sharedSecret, canonical)

        val ackRef = database.getReference("links").child(link.linkId).child("acks").child(commandId)
        val cmdRef = database.getReference("links").child(link.linkId).child("commands").child(commandId)

        val ackDeferred = CompletableDeferred<Pair<String, String?>>()
        val ackListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val status = snapshot.child("status").getValue(String::class.java) ?: return
                val error = snapshot.child("error").getValue(String::class.java)
                if (status == "Completed" || status == "Failed") {
                    ackDeferred.complete(status to error)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                ackDeferred.complete("Failed" to error.message)
            }
        }

        ackRef.addValueEventListener(ackListener)
        try {
            val cmdMap = mapOf(
                "commandId" to commandId,
                "senderUid" to uid,
                "senderDeviceId" to clientDeviceId,
                "targetDeviceId" to link.peerDeviceId,
                "commandType" to commandType,
                "payload" to payloadJson,
                "issuedAtMs" to issuedAtMs,
                "expiresAtMs" to expiresAtMs,
                "nonce" to nonce,
                "signature" to signature,
                "status" to "Created"
            )

            Log.i(TAG, "Dispatching remote command $commandType (id=$commandId)")
            cmdRef.setValue(cmdMap).await()

            val ackResult = withTimeoutOrNull(ACK_TIMEOUT_MS) {
                ackDeferred.await()
            }

            if (ackResult == null) {
                val cmdStatus = runCatching {
                    cmdRef.child("status").get().await().getValue(String::class.java)
                }.getOrNull()
                val msg = if (cmdStatus == "Delivered" || cmdStatus == "Validated" || cmdStatus == "Executing") {
                    "Command was delivered ($cmdStatus), but acknowledgement timed out."
                } else {
                    "Remote PC did not acknowledge command (status=${cmdStatus ?: "undelivered"})."
                }
                Log.w(TAG, "Remote command $commandId timed out: $msg")
                return@withContext OrbitResult.Error(Exception(msg))
            }

            val (status, error) = ackResult
            return@withContext if (status == "Completed") {
                Log.i(TAG, "Remote command $commandId completed.")
                OrbitResult.Success(Unit)
            } else {
                val errMsg = error ?: "Remote command failed."
                Log.w(TAG, "Remote command $commandId failed: $errMsg")
                OrbitResult.Error(Exception(errMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote command $commandType failed: ${e.message}")
            return@withContext OrbitResult.Error(e)
        } finally {
            ackRef.removeEventListener(ackListener)
        }
    }

    fun observeControls(onSnapshot: (LaptopControlsStatus) -> Unit): (() -> Unit)? {
        val link = linkStore.loadLink() ?: return null
        val ref: DatabaseReference = database.getReference("links").child(link.linkId).child("controls")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                try {
                    val wifi = snapshot.child("wifi")
                    val bt = snapshot.child("bluetooth")
                    val mic = snapshot.child("microphone")
                    val cam = snapshot.child("camera")
                    val vol = snapshot.child("volume")
                    val bright = snapshot.child("brightness")

                    val status = LaptopControlsStatus(
                        wifi = WifiControlStatus(
                            isAvailable = wifi.child("isAvailable").getValue(Boolean::class.java) ?: true,
                            isEnabled = wifi.child("isEnabled").getValue(Boolean::class.java) ?: true,
                            isConnected = wifi.child("isConnected").getValue(Boolean::class.java) ?: false,
                            ssid = wifi.child("ssid").getValue(String::class.java),
                            signal = wifi.child("signal").getValue(Int::class.java),
                            detail = wifi.child("detail").getValue(String::class.java)
                        ),
                        bluetooth = BluetoothControlStatus(
                            isAvailable = bt.child("isAvailable").getValue(Boolean::class.java) ?: true,
                            isEnabled = bt.child("isEnabled").getValue(Boolean::class.java) ?: true,
                            statusLabel = bt.child("statusLabel").getValue(String::class.java) ?: "No Devices",
                            detail = bt.child("detail").getValue(String::class.java)
                        ),
                        microphone = MicrophoneControlStatus(
                            isAvailable = mic.child("isAvailable").getValue(Boolean::class.java) ?: true,
                            isEnabled = mic.child("isEnabled").getValue(Boolean::class.java) ?: true,
                            isMuted = mic.child("isMuted").getValue(Boolean::class.java) ?: false,
                            statusLabel = mic.child("statusLabel").getValue(String::class.java) ?: "ON"
                        ),
                        camera = CameraControlStatus(
                            isAvailable = cam.child("isAvailable").getValue(Boolean::class.java) ?: true,
                            isEnabled = cam.child("isEnabled").getValue(Boolean::class.java) ?: true,
                            statusLabel = cam.child("statusLabel").getValue(String::class.java) ?: "Allowed",
                            detail = cam.child("detail").getValue(String::class.java)
                        ),
                        volume = VolumeControlStatus(
                            isAvailable = vol.child("isAvailable").getValue(Boolean::class.java) ?: true,
                            percentage = vol.child("percentage").getValue(Int::class.java) ?: 50,
                            isMuted = vol.child("isMuted").getValue(Boolean::class.java) ?: false
                        ),
                        brightness = BrightnessControlStatus(
                            isAvailable = bright.child("isAvailable").getValue(Boolean::class.java) ?: true,
                            percentage = bright.child("percentage").getValue(Int::class.java) ?: 100,
                            statusLabel = bright.child("statusLabel").getValue(String::class.java) ?: "100%",
                            detail = bright.child("detail").getValue(String::class.java)
                        )
                    )
                    onSnapshot(status)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse remote controls snapshot: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Remote controls listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        Log.i(TAG, "Attached scoped remote controls listener for linkId=${link.linkId}")
        return {
            ref.removeEventListener(listener)
            Log.d(TAG, "Detached remote controls listener for linkId=${link.linkId}")
        }
    }

    fun observeTelemetry(onTelemetry: (SystemTelemetry) -> Unit): (() -> Unit)? {
        val link = linkStore.loadLink() ?: return null
        val ref: DatabaseReference = database.getReference("links")
            .child(link.linkId)
            .child("devices")
            .child(link.peerDeviceId)
            .child("telemetry")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                try {
                    val cpu = snapshot.child("cpu")
                    val ram = snapshot.child("ram")
                    val wifi = snapshot.child("wifi")
                    val gpu = snapshot.child("gpu")

                    val telemetry = SystemTelemetry(
                        cpu = CpuStats(
                            name = cpu.child("name").getValue(String::class.java) ?: "Processor",
                            utilizationPercent = cpu.child("utilization").getValue(Double::class.java)?.toFloat() ?: 0f,
                            cores = cpu.child("cores").getValue(Int::class.java) ?: 8,
                            threads = cpu.child("threads").getValue(Int::class.java) ?: 16,
                            temperatureCelsius = cpu.child("temperature").getValue(Double::class.java)?.toFloat()
                        ),
                        ram = RamStats(
                            spec = ram.child("spec").getValue(String::class.java) ?: "RAM",
                            utilizationPercent = ram.child("utilization").getValue(Double::class.java)?.toFloat() ?: 0f,
                            usedGb = ram.child("usedGb").getValue(Double::class.java)?.toFloat() ?: 0f,
                            totalGb = ram.child("totalGb").getValue(Double::class.java)?.toFloat() ?: 0f,
                            availableGb = ram.child("availableGb").getValue(Double::class.java)?.toFloat() ?: 0f
                        ),
                        wifi = WifiStats(
                            name = wifi.child("name").getValue(String::class.java) ?: "LAN Direct Link",
                            downloadSpeedBps = wifi.child("downloadSpeedBps").getValue(Double::class.java) ?: 0.0,
                            uploadSpeedBps = wifi.child("uploadSpeedBps").getValue(Double::class.java) ?: 0.0,
                            signalDbm = wifi.child("signalDbm").getValue(Int::class.java),
                            latencyMs = wifi.child("latencyMs").getValue(Int::class.java)
                        ),
                        gpu = GpuStats(
                            name = gpu.child("name").getValue(String::class.java) ?: "Graphics",
                            utilizationPercent = gpu.child("utilization").getValue(Double::class.java)?.toFloat() ?: 0f,
                            vramMb = gpu.child("vramMb").getValue(Long::class.java),
                            temperatureCelsius = gpu.child("temperature").getValue(Double::class.java)?.toFloat()
                        ),
                        timestampMs = snapshot.child("timestampMs").getValue(Long::class.java) ?: System.currentTimeMillis()
                    )
                    onTelemetry(telemetry)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse remote telemetry: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Remote telemetry listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        Log.i(TAG, "Attached scoped remote telemetry listener for peer=${link.peerDeviceId}")
        return {
            ref.removeEventListener(listener)
            Log.d(TAG, "Detached remote telemetry listener for peer=${link.peerDeviceId}")
        }
    }

    fun jsonPayload(vararg pairs: Pair<String, Any?>): String {
        val obj = JSONObject()
        pairs.sortedBy { it.first }.forEach { (k, v) ->
            if (v != null) obj.put(k, v)
        }
        return obj.toString()
    }
}
