package com.pratyush.orbitmobile.core.firebase

import android.content.Context
import android.util.Log
import com.pratyush.orbitmobile.core.platform.KeystoreManager
import org.json.JSONObject
import java.util.Base64

/**
 * Bounded (single-item), Keystore-encrypted local store for a pending background
 * clipboard event. Never stores clipboard history and expires stale items.
 */
class PendingClipboardStore(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val maxPendingAgeMs: Long = DEFAULT_MAX_AGE_MS
) {
    companion object {
        private const val TAG = "OrbitPendingClipStore"
        private const val PREFS_NAME = "orbit_pending_clipboard_store"
        private const val KEY_CT = "pending_ct"
        private const val KEY_IV = "pending_iv"
        private const val KEY_LAST_NOTIFIED_ID = "last_notified_event_id"
        const val DEFAULT_MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun save(event: PendingClipboardEvent, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!ClipboardCrypto.isValidPlaintextSize(event.content)) return false
        return try {
            val json = JSONObject().apply {
                put("eventId", event.eventId)
                put("content", event.content)
                put("origin", event.origin)
                put("issuedAtMs", event.issuedAtMs)
                put("stagedAtMs", nowMs)
            }.toString()

            val (cipherBytes, ivBytes) = keystoreManager.encrypt(json.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_CT, Base64.getEncoder().encodeToString(cipherBytes))
                .putString(KEY_IV, Base64.getEncoder().encodeToString(ivBytes))
                .apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist pending clipboard item: ${e.message}")
            false
        }
    }

    fun load(nowMs: Long = System.currentTimeMillis()): PendingClipboardEvent? {
        return try {
            val ctB64 = prefs.getString(KEY_CT, null) ?: return null
            val ivB64 = prefs.getString(KEY_IV, null) ?: return null
            val plainBytes = keystoreManager.decrypt(
                Base64.getDecoder().decode(ctB64),
                Base64.getDecoder().decode(ivB64)
            )
            val obj = JSONObject(String(plainBytes, Charsets.UTF_8))
            val eventId = obj.optString("eventId", "")
            val content = obj.optString("content", "")
            val origin = obj.optString("origin", "Windows")
            val issuedAtMs = obj.optLong("issuedAtMs", 0L)
            val stagedAtMs = obj.optLong("stagedAtMs", issuedAtMs)

            if (eventId.isBlank() ||
                !ClipboardCrypto.isValidPlaintextSize(content) ||
                (nowMs - stagedAtMs) > maxPendingAgeMs
            ) {
                clear()
                return null
            }

            PendingClipboardEvent(
                eventId = eventId,
                content = content,
                origin = origin,
                issuedAtMs = issuedAtMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load pending clipboard item: ${e.message}")
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_CT).remove(KEY_IV).apply()
    }

    fun markNotified(eventId: String) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_ID, eventId).apply()
    }

    fun wasNotified(eventId: String): Boolean {
        return prefs.getString(KEY_LAST_NOTIFIED_ID, null) == eventId
    }
}
