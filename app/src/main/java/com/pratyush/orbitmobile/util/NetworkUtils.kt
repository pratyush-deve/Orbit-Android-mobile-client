package com.pratyush.orbitmobile.util

/**
 * Dedicated utility functions for IP and port validation.
 */
object NetworkUtils {

    private val IPV4_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    fun isValidIpv4(ip: String): Boolean {
        return IPV4_REGEX.matches(ip.trim())
    }

    fun isValidPort(port: Int): Boolean {
        return port in 1024..65535
    }

    fun parseHostAndPort(rawEndpoint: String, defaultPort: Int = 8765): Pair<String, Int>? {
        val parts = rawEndpoint.trim().split(":")
        val host = parts[0]
        if (!isValidIpv4(host)) return null
        val port = if (parts.size > 1) {
            parts[1].toIntOrNull() ?: defaultPort
        } else {
            defaultPort
        }
        return if (isValidPort(port)) Pair(host, port) else null
    }
}
