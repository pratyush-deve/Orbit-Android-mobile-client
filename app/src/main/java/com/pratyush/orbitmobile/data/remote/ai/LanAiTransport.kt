package com.pratyush.orbitmobile.data.remote.ai

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionOption
import com.pratyush.orbitmobile.domain.model.AiStreamEvent
import com.pratyush.orbitmobile.domain.model.AiToolActivity
import com.pratyush.orbitmobile.domain.repository.AiTransport
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * LAN HTTP/1.1 + SSE implementation of [AiTransport] communicating directly
 * with ORBIT Windows `MobileLanServer` over the active local connection.
 */
class LanAiTransport(
    private val connectionRepository: ConnectionRepository
) : AiTransport {

    private data class LanCredentials(
        val host: String,
        val port: Int,
        val authToken: String
    )

    private fun requireCredentials(): OrbitResult<LanCredentials> {
        val host = connectionRepository.activeHost
        val token = connectionRepository.activeAuthToken
        if (host.isNullOrBlank()) {
            return OrbitResult.Error(Exception("Not connected to Windows"))
        }
        if (token.isNullOrBlank()) {
            return OrbitResult.Error(Exception("Unauthorized"))
        }
        return OrbitResult.Success(LanCredentials(host, connectionRepository.activePort, token))
    }

    private fun openConnection(
        creds: LanCredentials,
        path: String,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        hasJsonBody: Boolean = false,
        acceptSse: Boolean = false
    ): HttpURLConnection {
        val url = URL("http://${creds.host}:${creds.port}$path")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            if (hasJsonBody) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (acceptSse) {
                setRequestProperty("Accept", "text/event-stream")
            }
            setRequestProperty("Authorization", "Bearer ${creds.authToken}")
            setRequestProperty("X-Orbit-Token", creds.authToken)
        }
    }

    override suspend fun getStatus(): OrbitResult<AcpStatus> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(creds, "/api/mobile/ai/status", "GET", 4000, 4000)
            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                OrbitResult.Success(
                    AcpStatus(
                        isAvailable = json.optBoolean("IsAvailable", false),
                        state = json.optString("State", "unavailable"),
                        backend = json.optString("Backend", "antigravity-acp"),
                        message = if (json.has("Message") && !json.isNull("Message")) json.optString("Message") else null,
                        permissionMode = json.optString("PermissionMode", "AutoApproveSafe")
                    )
                )
            } else {
                OrbitResult.Error(Exception("HTTP $code from AI status"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun getConversations(): OrbitResult<List<AiConversation>> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(creds, "/api/mobile/ai/conversations", "GET", 4000, 4000)
            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(responseText)
                val list = ArrayList<AiConversation>(arr.length())
                for (i in 0 until arr.length()) {
                    list.add(parseConversation(arr.getJSONObject(i)))
                }
                OrbitResult.Success(list)
            } else {
                OrbitResult.Error(Exception("HTTP $code from AI conversations"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun createConversation(
        title: String?,
        conversationId: String?
    ): OrbitResult<AiConversation> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/conversations/new",
                "POST",
                5000,
                5000,
                hasJsonBody = true
            )
            val body = JSONObject().apply {
                if (title != null) put("title", title)
                if (conversationId != null) put("id", conversationId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                OrbitResult.Success(parseConversation(JSONObject(responseText)))
            } else {
                OrbitResult.Error(Exception("HTTP $code from create AI conversation"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun getConversation(
        conversationId: String
    ): OrbitResult<Pair<AiConversation, List<AiMessage>>> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/conversations/$conversationId",
                "GET",
                5000,
                5000
            )
            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val conv = parseConversation(root.getJSONObject("conversation"))
                val mArr = root.getJSONArray("messages")
                val messages = ArrayList<AiMessage>(mArr.length())
                for (i in 0 until mArr.length()) {
                    messages.add(parseMessage(mArr.getJSONObject(i)))
                }
                OrbitResult.Success(Pair(conv, messages))
            } else {
                OrbitResult.Error(Exception("HTTP $code from fetch conversation"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun renameConversation(
        conversationId: String,
        title: String
    ): OrbitResult<AiConversation> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/conversations/$conversationId",
                "PUT",
                4000,
                4000,
                hasJsonBody = true
            )
            val body = JSONObject().apply {
                put("title", title)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                OrbitResult.Success(parseConversation(JSONObject(responseText)))
            } else {
                OrbitResult.Error(Exception("HTTP $code renaming conversation"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/conversations/$conversationId",
                "DELETE",
                4000,
                4000
            )
            val code = conn.responseCode
            if (code == 200) OrbitResult.Success(Unit)
            else OrbitResult.Error(Exception("HTTP $code deleting conversation"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun streamPrompt(
        conversationId: String,
        prompt: String,
        userMessageId: String?,
        assistantMessageId: String?,
        onEvent: (AiStreamEvent) -> Unit
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/prompt",
                "POST",
                6000,
                90000, // Antigravity reasoning can take up to 90 seconds
                hasJsonBody = true,
                acceptSse = true
            )
            val body = JSONObject().apply {
                put("conversationId", conversationId)
                put("prompt", prompt)
                if (userMessageId != null) put("userMessageId", userMessageId)
                if (assistantMessageId != null) put("assistantMessageId", assistantMessageId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val reader = conn.inputStream.bufferedReader()
                parseSseStream(reader, conversationId, onEvent)
                OrbitResult.Success(Unit)
            } else {
                OrbitResult.Error(Exception("HTTP $code streaming prompt"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun resolvePermission(
        requestId: String,
        optionId: String
    ): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/permission/resolve",
                "POST",
                4000,
                4000,
                hasJsonBody = true
            )
            val body = JSONObject().apply {
                put("requestId", requestId)
                put("optionId", optionId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                OrbitResult.Success(true)
            } else {
                OrbitResult.Error(Exception("HTTP $code resolving permission"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun setPolicy(policy: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/policy",
                "POST",
                4000,
                4000,
                hasJsonBody = true
            )
            val body = JSONObject().apply {
                put("policy", policy)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) OrbitResult.Success(Unit)
            else OrbitResult.Error(Exception("HTTP $code setting AI policy"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun cancelPrompt(conversationId: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val creds = when (val r = requireCredentials()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Not connected"))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(
                creds,
                "/api/mobile/ai/cancel",
                "POST",
                3000,
                3000,
                hasJsonBody = true
            )
            val body = JSONObject().apply {
                put("conversationId", conversationId)
            }
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code == 200) OrbitResult.Success(Unit)
            else OrbitResult.Error(Exception("HTTP $code cancelling prompt"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            conn?.disconnect()
        }
    }

    internal suspend fun parseSseStream(
        reader: BufferedReader,
        fallbackConversationId: String,
        onEvent: (AiStreamEvent) -> Unit
    ) {
        var currentEventType = "message"
        val ctx = currentCoroutineContext()
        var line: String? = reader.readLine()
        while (line != null && ctx.isActive) {
            val trimmed = line.trim()
            if (trimmed.startsWith("event:")) {
                currentEventType = trimmed.substring(6).trim()
            } else if (trimmed.startsWith("data:")) {
                val dataStr = trimmed.substring(5).trim()
                if (dataStr.isNotEmpty()) {
                    parseSseDataPayload(dataStr, currentEventType, fallbackConversationId)?.let(onEvent)
                }
            }
            line = reader.readLine()
        }
    }

    companion object {
        private const val TAG = "LanAiTransport"

        internal fun parseSseDataPayload(
            dataStr: String,
            currentEventType: String,
            fallbackConversationId: String
        ): AiStreamEvent? {
            return try {
                val dObj = JSONObject(dataStr)
                val evType = dObj.optString("Type", currentEventType)
                val cId = dObj.optString("ConversationId", fallbackConversationId)
                val mId = if (dObj.has("MessageId") && !dObj.isNull("MessageId")) dObj.getString("MessageId") else null
                val txt = if (dObj.has("Text") && !dObj.isNull("Text")) dObj.getString("Text") else null
                val tName = if (dObj.has("ToolName") && !dObj.isNull("ToolName")) dObj.getString("ToolName") else null
                val tStatus = if (dObj.has("ToolStatus") && !dObj.isNull("ToolStatus")) dObj.getString("ToolStatus") else null
                val tDetail = if (dObj.has("ToolDetail") && !dObj.isNull("ToolDetail")) dObj.getString("ToolDetail") else null
                val tCallId = when {
                    dObj.has("ToolCallId") && !dObj.isNull("ToolCallId") -> dObj.getString("ToolCallId")
                    dObj.has("toolCallId") && !dObj.isNull("toolCallId") -> dObj.getString("toolCallId")
                    else -> null
                } ?: run {
                    if (!tDetail.isNullOrBlank()) {
                        try {
                            val tdObj = JSONObject(tDetail)
                            when {
                                tdObj.has("toolCallId") -> tdObj.getString("toolCallId")
                                tdObj.has("id") -> tdObj.getString("id")
                                tdObj.has("callId") -> tdObj.getString("callId")
                                else -> null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                }
                val tTitle = when {
                    dObj.has("ToolTitle") && !dObj.isNull("ToolTitle") -> dObj.getString("ToolTitle")
                    dObj.has("toolTitle") && !dObj.isNull("toolTitle") -> dObj.getString("toolTitle")
                    else -> null
                }
                val rId = if (dObj.has("RequestId") && !dObj.isNull("RequestId")) dObj.getString("RequestId") else null
                val desc = if (dObj.has("Description") && !dObj.isNull("Description")) dObj.getString("Description") else null
                val err = if (dObj.has("Error") && !dObj.isNull("Error")) dObj.getString("Error") else null

                val optionsList = ArrayList<AiPermissionOption>()
                if (dObj.has("Options") && !dObj.isNull("Options")) {
                    val optArr = dObj.getJSONArray("Options")
                    for (k in 0 until optArr.length()) {
                        val o = optArr.getJSONObject(k)
                        optionsList.add(
                            AiPermissionOption(
                                optionId = o.getString("optionId"),
                                name = o.getString("name"),
                                kind = if (o.has("kind") && !o.isNull("kind")) o.getString("kind") else null
                            )
                        )
                    }
                }

                AiStreamEvent(
                    type = evType,
                    conversationId = cId,
                    messageId = mId,
                    text = txt,
                    toolCallId = tCallId,
                    toolTitle = tTitle,
                    toolName = tName,
                    toolStatus = tStatus,
                    toolDetail = tDetail,
                    requestId = rId,
                    description = desc,
                    options = optionsList,
                    error = err
                )
            } catch (_: Exception) {
                runCatching { Log.w(TAG, "Failed to parse SSE JSON: $dataStr") }
                null
            }
        }

        private fun parseConversation(obj: JSONObject): AiConversation {
            val now = System.currentTimeMillis()
            return AiConversation(
                id = obj.getString("Id"),
                title = obj.getString("Title"),
                backendId = obj.optString("BackendId", "antigravity-acp"),
                workspacePath = if (obj.has("WorkspacePath") && !obj.isNull("WorkspacePath")) obj.getString("WorkspacePath") else null,
                createdAtUtc = now,
                updatedAtUtc = now,
                isPinned = obj.optBoolean("IsPinned", false),
                isArchived = obj.optBoolean("IsArchived", false),
                origin = obj.optString("Origin", "Mobile"),
                isSynced = true
            )
        }

        private fun parseMessage(mObj: JSONObject): AiMessage {
            val toolsList = ArrayList<AiToolActivity>()
            if (mObj.has("ToolActivities") && !mObj.isNull("ToolActivities")) {
                val tArr = mObj.getJSONArray("ToolActivities")
                for (j in 0 until tArr.length()) {
                    val tObj = tArr.getJSONObject(j)
                    val rowId = tObj.getString("Id")
                    val callId = if (tObj.has("ToolCallId") && !tObj.isNull("ToolCallId")) {
                        tObj.getString("ToolCallId").ifBlank { rowId }
                    } else rowId

                    toolsList.add(
                        AiToolActivity(
                            id = rowId,
                            messageId = tObj.getString("MessageId"),
                            toolCallId = callId,
                            toolName = tObj.getString("ToolName"),
                            status = tObj.getString("Status"),
                            detail = if (tObj.has("ArgumentsSummary") && !tObj.isNull("ArgumentsSummary")) tObj.getString("ArgumentsSummary") else null,
                            timestampUtc = System.currentTimeMillis()
                        )
                    )
                }
            }

            return AiMessage(
                id = mObj.getString("Id"),
                conversationId = mObj.getString("ConversationId"),
                role = mObj.getString("Role"),
                content = mObj.getString("Content"),
                timestampUtc = System.currentTimeMillis(),
                generationStatus = mObj.optString("GenerationStatus", "completed"),
                isSynced = true,
                toolActivities = toolsList
            )
        }
    }
}
