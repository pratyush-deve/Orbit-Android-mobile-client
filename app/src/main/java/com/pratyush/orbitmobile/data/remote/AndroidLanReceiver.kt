package com.pratyush.orbitmobile.data.remote

import android.util.Log
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Embedded LAN receiver for ORBIT Mobile.
 * Listens on TCP port 8768 for inbound authenticated HTTP requests from ORBIT Windows,
 * specifically clipboard updates pushed from the desktop (POST /api/mobile/clipboard/from-windows).
 */
class AndroidLanReceiver(
    private val connectionRepository: ConnectionRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "AndroidLanReceiver"
        const val DEFAULT_PORT = 8768
    }

    private var serverSocket: ServerSocket? = null
    private var listeningJob: Job? = null
    var isRunning: Boolean = false
        private set

    fun start(port: Int = DEFAULT_PORT) {
        if (isRunning) return

        listeningJob = scope.launch {
            try {
                serverSocket = ServerSocket(port, 10, InetAddress.getByName("0.0.0.0")).apply {
                    reuseAddress = true
                }
                isRunning = true
                Log.i(TAG, "Android LAN Receiver listening on 0.0.0.0:$port")

                while (isActive && isRunning && serverSocket != null) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Error accepting client: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LAN receiver on port $port: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            listeningJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            Log.i(TAG, "Android LAN Receiver stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping LAN receiver: ${e.message}")
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 10000
                s.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), "UTF-8"))
                val writer = OutputStreamWriter(s.getOutputStream(), "UTF-8")

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val path = parts[1]
                Log.i(TAG, "Client connected: $method $path from ${s.remoteSocketAddress}")

                var contentLength = 0
                var authHeader: String? = null
                var line: String? = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val name = line.substring(0, colonIdx).trim().lowercase()
                        val value = line.substring(colonIdx + 1).trim()
                        if (name == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        } else if (name == "authorization" || name == "x-orbit-token") {
                            authHeader = value
                        }
                    }
                    line = reader.readLine()
                }

                // Read request body
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(buf, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    String(buf, 0, readTotal)
                } else ""

                // Authenticate request: check bearer token
                val activeToken = connectionRepository.activeAuthToken
                if (!activeToken.isNullOrBlank() && !authHeader.isNullOrBlank()) {
                    val incomingToken = if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                        authHeader.substring(7).trim()
                    } else authHeader.trim()

                    if (incomingToken != activeToken) {
                        Log.w(TAG, "Unauthorized request: token mismatch")
                        writeResponse(s, writer, 401, "{\"error\":\"Unauthorized\"}")
                        return
                    }
                }

                // Route endpoint
                val cleanPath = if (path.contains('?')) path.substring(0, path.indexOf('?')) else path
                when {
                    cleanPath == "/api/mobile/clipboard/from-windows" && method == "POST" -> {
                        handleClipboardFromWindows(s, body, writer)
                    }
                    cleanPath == "/api/mobile/ping" -> {
                        writeResponse(s, writer, 200, "{\"status\":\"pong\"}")
                    }
                    cleanPath == "/api/mobile/disconnect" && method == "POST" -> {
                        Log.i(TAG, "Received disconnect notification from Windows.")
                        writeResponse(s, writer, 200, "{\"status\":\"ok\"}")
                        scope.launch {
                            connectionRepository.disconnect()
                        }
                    }
                    cleanPath == "/api/mobile/reconnect" && method == "POST" -> {
                        Log.i(TAG, "Received reconnect command from Windows.")
                        writeResponse(s, writer, 200, "{\"status\":\"ok\"}")
                        scope.launch {
                            connectionRepository.attemptReconnect()
                        }
                    }
                    else -> {
                        writeResponse(s, writer, 404, "{\"error\":\"Not Found\"}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing client request: ${e.message}", e)
            }
        }
    }

    private fun handleClipboardFromWindows(socket: Socket, body: String, writer: OutputStreamWriter) {
        if (body.isBlank()) {
            writeResponse(socket, writer, 400, "{\"error\":\"Empty payload\"}")
            return
        }

        try {
            val json = JSONObject(body)
            val id = json.optString("id", "")
            val type = json.optString("type", "Text")
            val textContent = json.optString("textContent", "")
            val urlContent = json.optString("urlContent", "")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val deviceOrigin = json.optString("deviceOrigin", "Windows")

            val content = if (textContent.isNotBlank()) textContent else urlContent
            if (content.isNotBlank()) {
                Log.i(TAG, "Received clipboard from Windows: '$content' ($type, origin=$deviceOrigin)")
                connectionRepository.receiveIncomingClipboardFromWindows(
                    id = id,
                    type = type,
                    content = content,
                    timestamp = timestamp,
                    origin = deviceOrigin
                )
                writeResponse(socket, writer, 200, "{\"status\":\"ok\"}")
            } else {
                writeResponse(socket, writer, 400, "{\"error\":\"No clipboard text content\"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming clipboard JSON: ${e.message}", e)
            writeResponse(socket, writer, 400, "{\"error\":\"Malformed JSON\"}")
        }
    }

    private fun writeResponse(socket: Socket, writer: OutputStreamWriter, statusCode: Int, jsonBody: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
        }
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        writer.write("HTTP/1.1 $statusCode $statusText\r\n")
        writer.write("Content-Type: application/json; charset=utf-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(jsonBody)
        writer.flush()
    }
}
