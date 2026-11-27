package com.pratyush.orbitmobile.networking.protocol

/**
 * Standard data frame exchanged across transport channels.
 */
data class OrbitPacket(
    val packetId: String,
    val type: PacketType,
    val timestampEpochMs: Long,
    val payloadUtf8: String,
    val signature: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OrbitPacket

        if (packetId != other.packetId) return false
        if (type != other.type) return false
        if (timestampEpochMs != other.timestampEpochMs) return false
        if (payloadUtf8 != other.payloadUtf8) return false
        if (signature != null) {
            if (other.signature == null) return false
            if (!signature.contentEquals(other.signature)) return false
        } else if (other.signature != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packetId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + timestampEpochMs.hashCode()
        result = 31 * result + payloadUtf8.hashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }
}
