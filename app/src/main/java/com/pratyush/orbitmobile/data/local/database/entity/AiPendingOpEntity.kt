package com.pratyush.orbitmobile.data.local.database.entity

/**
 * Persistent offline outbox entry for AI conversation mutations
 * (create, rename, delete) awaiting delivery to Windows ORBIT.
 */
data class AiPendingOpEntity(
    val opId: String,
    val conversationId: String,
    val opType: String, // "create", "rename", "delete"
    val payloadJson: String,
    val createdAtMs: Long,
    val retryCount: Int = 0,
    val status: String = STATUS_PENDING, // "pending", "in_flight", "failed_permanent"
    val lastError: String? = null
) {
    companion object {
        const val OP_CREATE = "create"
        const val OP_RENAME = "rename"
        const val OP_DELETE = "delete"

        const val STATUS_PENDING = "pending"
        const val STATUS_IN_FLIGHT = "in_flight"
        const val STATUS_FAILED_PERMANENT = "failed_permanent"
    }
}
