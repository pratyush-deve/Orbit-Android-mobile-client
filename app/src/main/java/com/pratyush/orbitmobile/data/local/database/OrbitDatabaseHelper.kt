package com.pratyush.orbitmobile.data.local.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pratyush.orbitmobile.data.local.database.dao.AiChatDao
import com.pratyush.orbitmobile.data.local.database.dao.DeviceDao
import com.pratyush.orbitmobile.data.local.database.entity.AiConversationEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiMessageEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiToolActivityEntity
import com.pratyush.orbitmobile.data.local.database.entity.DeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Robust SQLiteOpenHelper implementing [OrbitDatabase] with WAL mode, transactions,
 * and asynchronous Flow updates.
 */
class OrbitDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, OrbitDatabase.DATABASE_NAME, null, 2),
    OrbitDatabase {

    private val conversationChangeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val messageChangeTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                ip_address TEXT NOT NULL,
                port INTEGER NOT NULL,
                is_paired INTEGER NOT NULL,
                last_seen_epoch_ms INTEGER NOT NULL,
                os_info TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_conversations (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                backend_id TEXT NOT NULL DEFAULT 'antigravity-acp',
                workspace_path TEXT,
                created_at_utc INTEGER NOT NULL,
                updated_at_utc INTEGER NOT NULL,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                is_archived INTEGER NOT NULL DEFAULT 0,
                origin TEXT NOT NULL DEFAULT 'Mobile',
                is_synced INTEGER NOT NULL DEFAULT 1
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_messages (
                id TEXT PRIMARY KEY NOT NULL,
                conversation_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp_utc INTEGER NOT NULL,
                generation_status TEXT NOT NULL DEFAULT 'completed',
                is_synced INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_tool_activities (
                id TEXT PRIMARY KEY NOT NULL,
                message_id TEXT NOT NULL,
                tool_call_id TEXT NOT NULL DEFAULT '',
                tool_name TEXT NOT NULL,
                status TEXT NOT NULL,
                detail TEXT,
                timestamp_utc INTEGER NOT NULL,
                FOREIGN KEY(message_id) REFERENCES ai_messages(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_updated ON ai_conversations(updated_at_utc DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv ON ai_messages(conversation_id, timestamp_utc ASC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tool_activities_call ON ai_tool_activities(message_id, tool_call_id);")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE ai_tool_activities ADD COLUMN tool_call_id TEXT NOT NULL DEFAULT ''")
        } catch (_: Exception) {}
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    override fun deviceDao(): DeviceDao = object : DeviceDao {
        override fun getAllDevices(): Flow<List<DeviceEntity>> = flow {
            emit(queryDevices(null))
        }.flowOn(Dispatchers.IO)

        override fun getPairedDevices(): Flow<List<DeviceEntity>> = flow {
            emit(queryDevices("is_paired = 1"))
        }.flowOn(Dispatchers.IO)

        override suspend fun insertDevice(device: DeviceEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("id", device.id)
                put("name", device.name)
                put("ip_address", device.ipAddress)
                put("port", device.port)
                put("is_paired", if (device.isPaired) 1 else 0)
                put("last_seen_epoch_ms", device.lastSeenEpochMs)
                put("os_info", device.osInfo)
            }
            writableDatabase.insertWithOnConflict("devices", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Unit
        }

        override suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("devices", "id = ?", arrayOf(deviceId))
            Unit
        }

        private fun queryDevices(selection: String?): List<DeviceEntity> {
            val list = mutableListOf<DeviceEntity>()
            val cursor = readableDatabase.query("devices", null, selection, null, null, null, null)
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow("id")
                val nameIdx = it.getColumnIndexOrThrow("name")
                val ipIdx = it.getColumnIndexOrThrow("ip_address")
                val portIdx = it.getColumnIndexOrThrow("port")
                val pairedIdx = it.getColumnIndexOrThrow("is_paired")
                val lastSeenIdx = it.getColumnIndexOrThrow("last_seen_epoch_ms")
                val osIdx = it.getColumnIndexOrThrow("os_info")

                while (it.moveToNext()) {
                    list.add(
                        DeviceEntity(
                            id = it.getString(idIdx),
                            name = it.getString(nameIdx),
                            ipAddress = it.getString(ipIdx),
                            port = it.getInt(portIdx),
                            isPaired = it.getInt(pairedIdx) == 1,
                            lastSeenEpochMs = it.getLong(lastSeenIdx),
                            osInfo = it.getString(osIdx)
                        )
                    )
                }
            }
            return list
        }
    }

    override fun aiChatDao(): AiChatDao = object : AiChatDao {
        override fun getConversationsFlow(): Flow<List<AiConversationEntity>> = flow {
            emit(getConversations())
            conversationChangeTrigger.asSharedFlow().collect {
                emit(getConversations())
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun getConversations(): List<AiConversationEntity> = withContext(Dispatchers.IO) {
            val list = mutableListOf<AiConversationEntity>()
            val cursor = readableDatabase.query(
                "ai_conversations",
                null,
                "origin = ?",
                arrayOf("Mobile"),
                null,
                null,
                "is_pinned DESC, updated_at_utc DESC"
            )
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow("id")
                val titleIdx = it.getColumnIndexOrThrow("title")
                val backendIdx = it.getColumnIndexOrThrow("backend_id")
                val wsIdx = it.getColumnIndexOrThrow("workspace_path")
                val createdIdx = it.getColumnIndexOrThrow("created_at_utc")
                val updatedIdx = it.getColumnIndexOrThrow("updated_at_utc")
                val pinnedIdx = it.getColumnIndexOrThrow("is_pinned")
                val archivedIdx = it.getColumnIndexOrThrow("is_archived")
                val originIdx = it.getColumnIndexOrThrow("origin")
                val syncedIdx = it.getColumnIndexOrThrow("is_synced")

                while (it.moveToNext()) {
                    list.add(
                        AiConversationEntity(
                            id = it.getString(idIdx),
                            title = it.getString(titleIdx),
                            backendId = it.getString(backendIdx),
                            workspacePath = if (it.isNull(wsIdx)) null else it.getString(wsIdx),
                            createdAtUtc = it.getLong(createdIdx),
                            updatedAtUtc = it.getLong(updatedIdx),
                            isPinned = it.getInt(pinnedIdx) == 1,
                            isArchived = it.getInt(archivedIdx) == 1,
                            origin = it.getString(originIdx),
                            isSynced = it.getInt(syncedIdx) == 1
                        )
                    )
                }
            }
            list
        }

        override suspend fun getConversationById(id: String): AiConversationEntity? = withContext(Dispatchers.IO) {
            val cursor = readableDatabase.query("ai_conversations", null, "id = ?", arrayOf(id), null, null, null)
            cursor.use {
                if (it.moveToFirst()) {
                    AiConversationEntity(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        backendId = it.getString(it.getColumnIndexOrThrow("backend_id")),
                        workspacePath = it.getString(it.getColumnIndexOrThrow("workspace_path")),
                        createdAtUtc = it.getLong(it.getColumnIndexOrThrow("created_at_utc")),
                        updatedAtUtc = it.getLong(it.getColumnIndexOrThrow("updated_at_utc")),
                        isPinned = it.getInt(it.getColumnIndexOrThrow("is_pinned")) == 1,
                        isArchived = it.getInt(it.getColumnIndexOrThrow("is_archived")) == 1,
                        origin = it.getString(it.getColumnIndexOrThrow("origin")),
                        isSynced = it.getInt(it.getColumnIndexOrThrow("is_synced")) == 1
                    )
                } else null
            }
        }

        override suspend fun upsertConversation(conversation: AiConversationEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("backend_id", conversation.backendId)
                put("workspace_path", conversation.workspacePath)
                put("created_at_utc", conversation.createdAtUtc)
                put("updated_at_utc", conversation.updatedAtUtc)
                put("is_pinned", if (conversation.isPinned) 1 else 0)
                put("is_archived", if (conversation.isArchived) 1 else 0)
                put("origin", conversation.origin)
                put("is_synced", if (conversation.isSynced) 1 else 0)
            }
            writableDatabase.insertWithOnConflict("ai_conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            conversationChangeTrigger.tryEmit(Unit)
            Unit
        }

        override suspend fun upsertConversations(conversations: List<AiConversationEntity>) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (conv in conversations) {
                    val values = ContentValues().apply {
                        put("id", conv.id)
                        put("title", conv.title)
                        put("backend_id", conv.backendId)
                        put("workspace_path", conv.workspacePath)
                        put("created_at_utc", conv.createdAtUtc)
                        put("updated_at_utc", conv.updatedAtUtc)
                        put("is_pinned", if (conv.isPinned) 1 else 0)
                        put("is_archived", if (conv.isArchived) 1 else 0)
                        put("origin", conv.origin)
                        put("is_synced", if (conv.isSynced) 1 else 0)
                    }
                    db.insertWithOnConflict("ai_conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            conversationChangeTrigger.tryEmit(Unit)
            Unit
        }

        override suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("ai_conversations", "id = ?", arrayOf(id))
            writableDatabase.delete("ai_messages", "conversation_id = ?", arrayOf(id))
            conversationChangeTrigger.tryEmit(Unit)
            Unit
        }

        override fun getMessagesFlow(conversationId: String): Flow<List<AiMessageEntity>> = flow {
            emit(getMessages(conversationId))
            messageChangeTrigger.asSharedFlow().collect { touchedId ->
                if (touchedId == conversationId) {
                    emit(getMessages(conversationId))
                }
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun getMessages(conversationId: String): List<AiMessageEntity> = withContext(Dispatchers.IO) {
            val list = mutableListOf<AiMessageEntity>()
            val cursor = readableDatabase.query(
                "ai_messages",
                null,
                "conversation_id = ?",
                arrayOf(conversationId),
                null,
                null,
                "timestamp_utc ASC"
            )
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow("id")
                val convIdx = it.getColumnIndexOrThrow("conversation_id")
                val roleIdx = it.getColumnIndexOrThrow("role")
                val contentIdx = it.getColumnIndexOrThrow("content")
                val timeIdx = it.getColumnIndexOrThrow("timestamp_utc")
                val statusIdx = it.getColumnIndexOrThrow("generation_status")
                val syncedIdx = it.getColumnIndexOrThrow("is_synced")

                while (it.moveToNext()) {
                    list.add(
                        AiMessageEntity(
                            id = it.getString(idIdx),
                            conversationId = it.getString(convIdx),
                            role = it.getString(roleIdx),
                            content = it.getString(contentIdx),
                            timestampUtc = it.getLong(timeIdx),
                            generationStatus = it.getString(statusIdx),
                            isSynced = it.getInt(syncedIdx) == 1
                        )
                    )
                }
            }
            list
        }

        override suspend fun upsertMessage(message: AiMessageEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("id", message.id)
                put("conversation_id", message.conversationId)
                put("role", message.role)
                put("content", message.content)
                put("timestamp_utc", message.timestampUtc)
                put("generation_status", message.generationStatus)
                put("is_synced", if (message.isSynced) 1 else 0)
            }
            writableDatabase.insertWithOnConflict("ai_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            messageChangeTrigger.tryEmit(message.conversationId)
            Unit
        }

        override suspend fun upsertMessages(messages: List<AiMessageEntity>) = withContext(Dispatchers.IO) {
            if (messages.isEmpty()) return@withContext
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (msg in messages) {
                    val values = ContentValues().apply {
                        put("id", msg.id)
                        put("conversation_id", msg.conversationId)
                        put("role", msg.role)
                        put("content", msg.content)
                        put("timestamp_utc", msg.timestampUtc)
                        put("generation_status", msg.generationStatus)
                        put("is_synced", if (msg.isSynced) 1 else 0)
                    }
                    db.insertWithOnConflict("ai_messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            messages.firstOrNull()?.let { messageChangeTrigger.tryEmit(it.conversationId) }
            Unit
        }

        override suspend fun updateMessageContentAndStatus(id: String, content: String, status: String, isSynced: Boolean) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("content", content)
                put("generation_status", status)
                put("is_synced", if (isSynced) 1 else 0)
            }
            writableDatabase.update("ai_messages", values, "id = ?", arrayOf(id))
            Unit
        }

        override suspend fun deleteMessagesForConversation(conversationId: String) = withContext(Dispatchers.IO) {
            writableDatabase.delete("ai_messages", "conversation_id = ?", arrayOf(conversationId))
            messageChangeTrigger.tryEmit(conversationId)
            Unit
        }

        override suspend fun getToolActivities(messageId: String): List<AiToolActivityEntity> = withContext(Dispatchers.IO) {
            val list = mutableListOf<AiToolActivityEntity>()
            val cursor = readableDatabase.query(
                "ai_tool_activities",
                null,
                "message_id = ?",
                arrayOf(messageId),
                null,
                null,
                "timestamp_utc ASC"
            )
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow("id")
                val msgIdx = it.getColumnIndexOrThrow("message_id")
                val toolIdx = it.getColumnIndexOrThrow("tool_name")
                val statusIdx = it.getColumnIndexOrThrow("status")
                val detailIdx = it.getColumnIndexOrThrow("detail")
                val timeIdx = it.getColumnIndexOrThrow("timestamp_utc")
                val callIdIdx = it.getColumnIndex("tool_call_id")

                while (it.moveToNext()) {
                    val rowId = it.getString(idIdx)
                    val callId = if (callIdIdx != -1 && !it.isNull(callIdIdx)) {
                        it.getString(callIdIdx).ifBlank { rowId }
                    } else rowId

                    list.add(
                        AiToolActivityEntity(
                            id = rowId,
                            messageId = it.getString(msgIdx),
                            toolCallId = callId,
                            toolName = it.getString(toolIdx),
                            status = it.getString(statusIdx),
                            detail = if (it.isNull(detailIdx)) null else it.getString(detailIdx),
                            timestampUtc = it.getLong(timeIdx)
                        )
                    )
                }
            }
            list
        }

        override suspend fun upsertToolActivity(activity: AiToolActivityEntity) = withContext(Dispatchers.IO) {
            val callId = activity.toolCallId.ifBlank { activity.id }
            val cursor = readableDatabase.rawQuery(
                "SELECT id, status, detail, tool_name FROM ai_tool_activities WHERE message_id = ? AND (tool_call_id = ? OR id = ?) LIMIT 1",
                arrayOf(activity.messageId, callId, activity.id)
            )

            data class ExistingToolRow(val id: String, val status: String, val detail: String?, val toolName: String)

            val existing = cursor.use { c ->
                if (c.moveToFirst()) {
                    ExistingToolRow(
                        id = c.getString(0),
                        status = c.getString(1),
                        detail = if (c.isNull(2)) null else c.getString(2),
                        toolName = c.getString(3)
                    )
                } else null
            }

            val terminalStatuses = setOf("completed", "failed", "cancelled", "done", "error")
            val isExistingTerminal = existing != null && existing.status.lowercase() in terminalStatuses
            val isNewTerminal = activity.status.lowercase() in terminalStatuses

            // Out-of-order protection: terminal status cannot revert to running
            val resolvedStatus = if (isExistingTerminal && !isNewTerminal) {
                existing.status
            } else {
                activity.status
            }

            val resolvedDetail = if (!activity.detail.isNullOrBlank()) activity.detail else existing?.detail
            val resolvedToolName = if (activity.toolName.isNotBlank() && activity.toolName != "Tool") {
                activity.toolName
            } else {
                existing?.toolName ?: activity.toolName
            }

            val values = ContentValues().apply {
                put("id", existing?.id ?: activity.id)
                put("message_id", activity.messageId)
                put("tool_call_id", callId)
                put("tool_name", resolvedToolName)
                put("status", resolvedStatus)
                put("detail", resolvedDetail)
                put("timestamp_utc", activity.timestampUtc)
            }
            writableDatabase.insertWithOnConflict("ai_tool_activities", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Unit
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: OrbitDatabaseHelper? = null

        fun getInstance(context: Context): OrbitDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OrbitDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
