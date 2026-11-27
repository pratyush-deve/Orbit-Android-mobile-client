package com.pratyush.orbitmobile.core.firebase

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pratyush.orbitmobile.core.platform.KeystoreManager
import org.json.JSONObject

data class RemoteLinkCredentials(
    val linkId: String,
    val peerDeviceId: String,
    val peerUid: String,
    val sharedSecret: String
)

/**
 * Persists remote device-link associations and shared secrets using Android Keystore AES/GCM encryption.
 * Never stores or logs plaintext shared secrets.
 */
class RemoteLinkStore(
    private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val TAG = "OrbitRemoteLinkStore"
        private const val PREFS_NAME = "orbit_remote_links"
        private const val KEY_ACTIVE_LINK_CT = "active_link_ct"
        private const val KEY_ACTIVE_LINK_IV = "active_link_iv"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLink(credentials: RemoteLinkCredentials) {
        try {
            val json = JSONObject().apply {
                put("linkId", credentials.linkId)
                put("peerDeviceId", credentials.peerDeviceId)
                put("peerUid", credentials.peerUid)
                put("sharedSecret", credentials.sharedSecret)
            }.toString()

            val (cipherBytes, ivBytes) = keystoreManager.encrypt(json.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_ACTIVE_LINK_CT, Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
                .putString(KEY_ACTIVE_LINK_IV, Base64.encodeToString(ivBytes, Base64.NO_WRAP))
                .apply()

            Log.i(TAG, "Saved Keystore-encrypted remote link (linkId=${credentials.linkId}, peer=${credentials.peerDeviceId})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save remote link: ${e.message}")
        }
    }

    fun loadLink(): RemoteLinkCredentials? {
        return try {
            val ctB64 = prefs.getString(KEY_ACTIVE_LINK_CT, null) ?: return null
            val ivB64 = prefs.getString(KEY_ACTIVE_LINK_IV, null) ?: return null
            val cipherBytes = Base64.decode(ctB64, Base64.NO_WRAP)
            val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
            val plainBytes = keystoreManager.decrypt(cipherBytes, ivBytes)
            val json = JSONObject(String(plainBytes, Charsets.UTF_8))

            val linkId = json.optString("linkId", "")
            val peerDeviceId = json.optString("peerDeviceId", "")
            val peerUid = json.optString("peerUid", "")
            val sharedSecret = json.optString("sharedSecret", "")
            if (linkId.isBlank() || sharedSecret.isBlank()) return null

            RemoteLinkCredentials(
                linkId = linkId,
                peerDeviceId = peerDeviceId,
                peerUid = peerUid,
                sharedSecret = sharedSecret
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not load remote link credentials: ${e.message}")
            null
        }
    }

    fun clearLink() {
        prefs.edit()
            .remove(KEY_ACTIVE_LINK_CT)
            .remove(KEY_ACTIVE_LINK_IV)
            .apply()
        Log.i(TAG, "Cleared remote link credentials.")
    }
}
