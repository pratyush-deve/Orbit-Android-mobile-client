package com.pratyush.orbitmobile.networking.protocol

/**
 * Protocol packet classification for communication between ORBIT Mobile and ORBIT Windows.
 */
enum class PacketType {
    HANDSHAKE,
    HEARTBEAT,
    SYSTEM_METRICS,
    REMOTE_COMMAND,
    COMMAND_RESPONSE,
    DISCONNECT
}
