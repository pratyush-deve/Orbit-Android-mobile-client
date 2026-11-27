package com.pratyush.orbitmobile.domain.model

import java.security.MessageDigest
import java.util.UUID

/**
 * Domain model representing a synchronized clipboard entry with stable identity.
 */
data class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "Text",
    val content: String,
    val preview: String = if (content.length > 90) content.take(90) + "..." else content,
    val timestamp: Long = System.currentTimeMillis(),
    val origin: String = "Phone", // "Phone" or "Windows"
    val contentHash: String = computeHash(content),
    val deviceId: String = ""
) {
    companion object {
        fun computeHash(text: String): String {
            if (text.isBlank()) return ""
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(text.trim().toByteArray(Charsets.UTF_8))
                hashBytes.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                text.trim().hashCode().toString()
            }
        }
    }
}
