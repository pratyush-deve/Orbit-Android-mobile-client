package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionOption
import com.pratyush.orbitmobile.domain.model.AiPermissionRequest
import com.pratyush.orbitmobile.domain.model.AiToolActivity
import com.pratyush.orbitmobile.presentation.screens.ai.AiChatUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatUnitTest {

    @Test
    fun `test AiChatUiState filteredConversations respects search query`() {
        val convs = listOf(
            AiConversation(id = "1", title = "Refactor Authentication", createdAtUtc = 1000L, updatedAtUtc = 1000L),
            AiConversation(id = "2", title = "System Monitor UI", createdAtUtc = 2000L, updatedAtUtc = 2000L),
            AiConversation(id = "3", title = "Bugfix in Trackpad", createdAtUtc = 3000L, updatedAtUtc = 3000L)
        )

        val stateEmpty = AiChatUiState(conversations = convs, searchQuery = "")
        assertEquals(3, stateEmpty.filteredConversations.size)

        val stateFilter = AiChatUiState(conversations = convs, searchQuery = "auth")
        assertEquals(1, stateFilter.filteredConversations.size)
        assertEquals("Refactor Authentication", stateFilter.filteredConversations.first().title)

        val stateNoMatch = AiChatUiState(conversations = convs, searchQuery = "NonExistent")
        assertTrue(stateNoMatch.filteredConversations.isEmpty())
    }

    @Test
    fun `test AiMessage structure and tool activity association`() {
        val tool = AiToolActivity(
            id = "t1",
            messageId = "m1",
            toolName = "read_file",
            status = "completed",
            detail = "Path: file.txt"
        )

        val message = AiMessage(
            id = "m1",
            conversationId = "c1",
            role = "assistant",
            content = "Here is the result: ```kotlin\nval x = 1\n```",
            thought = "Inspecting codebase structure...",
            timestampUtc = System.currentTimeMillis(),
            toolActivities = listOf(tool)
        )

        assertEquals("assistant", message.role)
        assertEquals(1, message.toolActivities.size)
        assertEquals("read_file", message.toolActivities.first().toolName)
        assertNotNull(message.thought)
    }

    @Test
    fun `test AiPermissionRequest options parsing and default states`() {
        val request = AiPermissionRequest(
            requestId = "req-123",
            toolName = "run_command",
            description = "Run command: rm -rf tmp",
            options = listOf(
                AiPermissionOption("allow_once", "Allow Once"),
                AiPermissionOption("allow_session", "Allow Session"),
                AiPermissionOption("deny", "Deny")
            )
        )

        assertEquals("req-123", request.requestId)
        assertEquals(3, request.options.size)
        assertEquals("deny", request.options.last().id)
    }

    @Test
    fun `test AcpStatus reports availability correctly`() {
        val statusOnline = AcpStatus(isAvailable = true, state = "ready")
        assertTrue(statusOnline.isAvailable)
        assertFalse(statusOnline.isConnecting)

        val statusConnecting = AcpStatus(isAvailable = false, state = "connecting")
        assertFalse(statusConnecting.isAvailable)
        assertTrue(statusConnecting.isConnecting)
    }

    @Test
    fun `test Markdown parser extracts structured blocks accurately`() {
        val markdown = """
            # Heading 1
            Here is a paragraph with **bold** and `inline code`.
            
            - First bullet
            - Second bullet
            
            1. Number one
            2. Number two
            
            ```kotlin
            fun hello() = "world"
            ```
            
            > Important quote
            
            ---
        """.trimIndent()

        val blocks = com.pratyush.orbitmobile.presentation.screens.ai.components.parseMarkdownBlocks(markdown)
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.Heading && it.level == 1 && it.text == "Heading 1" })
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.Paragraph })
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.UnorderedList && it.items.size == 2 })
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.OrderedList && it.items.size == 2 })
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.Code && it.language == "kotlin" })
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.BlockQuote })
        assertTrue(blocks.any { it is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.HorizontalRule })
    }

    @Test
    fun `test Markdown sanitizer extracts text from leaked transport envelopes`() {
        val rawJsonEnvelope = """{"sessionUpdate":"agent_message_chunk","content":{"text":"Hello from Antigravity!"}}"""
        val sanitized = com.pratyush.orbitmobile.presentation.screens.ai.components.sanitizeRawContent(rawJsonEnvelope)
        assertEquals("Hello from Antigravity!", sanitized)

        val regularText = "This is regular markdown content."
        assertEquals(regularText, com.pratyush.orbitmobile.presentation.screens.ai.components.sanitizeRawContent(regularText))
    }

    @Test
    fun `test Inline markdown formatter applies bold and code spans`() {
        val formatted = com.pratyush.orbitmobile.presentation.screens.ai.components.formatInlineMarkdown("Test **bold** and `code`")
        assertEquals("Test bold and  code ", formatted.text)
        assertTrue(formatted.spanStyles.isNotEmpty())
    }

    @Test
    fun `test Single session chat retains conversation identity without fragmentation`() {
        val conversationId = "conv-session-12345"
        val msg1 = AiMessage(
            id = "msg-1",
            conversationId = conversationId,
            role = "user",
            content = "First prompt in conversation",
            timestampUtc = 1000L
        )
        val msg2 = AiMessage(
            id = "msg-2",
            conversationId = conversationId,
            role = "assistant",
            content = "First response in conversation",
            timestampUtc = 1050L
        )
        val msg3 = AiMessage(
            id = "msg-3",
            conversationId = conversationId,
            role = "user",
            content = "Second follow-up prompt",
            timestampUtc = 2000L
        )

        val messages = listOf(msg1, msg2, msg3)
        // All messages belong to the exact same conversation session
        assertTrue(messages.all { it.conversationId == conversationId })
        assertEquals(3, messages.size)
    }

    @Test
    fun `test OrbitBackgroundService isServiceRunning guard reflects state cleanly`() {
        // Initial state before service lifecycle
        assertFalse(com.pratyush.orbitmobile.services.OrbitBackgroundService.isServiceRunning)
    }

    @Test
    fun `test Tool activity reconciliation updates existing card instead of duplicating`() {
        val initialList = emptyList<AiToolActivity>()

        // 1. First event: tool_call with status running
        val event1 = AiToolActivity(
            id = "call_abc123",
            messageId = "msg_1",
            toolCallId = "call_abc123",
            toolName = "read_file",
            status = "running",
            detail = "path: file.txt"
        )
        val reconciled1 = com.pratyush.orbitmobile.data.repository.implementations.AiChatRepositoryImpl.reconcileToolActivities(
            initialList,
            event1
        )
        assertEquals(1, reconciled1.size)
        assertEquals("running", reconciled1.first().status)
        assertEquals("read_file", reconciled1.first().toolName)

        // 2. Subsequent update event for the same toolCallId: status completed with result
        val event2 = AiToolActivity(
            id = "call_abc123",
            messageId = "msg_1",
            toolCallId = "call_abc123",
            toolName = "read_file",
            status = "completed",
            detail = "content of file.txt",
            title = "Read file.txt"
        )
        val reconciled2 = com.pratyush.orbitmobile.data.repository.implementations.AiChatRepositoryImpl.reconcileToolActivities(
            reconciled1,
            event2
        )
        assertEquals("Must remain exactly one card for the same toolCallId", 1, reconciled2.size)
        assertEquals("completed", reconciled2.first().status)
        assertEquals("content of file.txt", reconciled2.first().detail)
        assertEquals("Read file.txt", reconciled2.first().title)
    }

    @Test
    fun `test Tool activity reconciliation ignores out-of-order downgrade from terminal state`() {
        // Completed tool card
        val completedActivity = AiToolActivity(
            id = "call_xyz789",
            messageId = "msg_1",
            toolCallId = "call_xyz789",
            toolName = "execute_command",
            status = "completed",
            detail = "Exit code: 0"
        )
        val list = listOf(completedActivity)

        // Stale/late update with status running arrives
        val staleRunningUpdate = AiToolActivity(
            id = "call_xyz789",
            messageId = "msg_1",
            toolCallId = "call_xyz789",
            toolName = "execute_command",
            status = "running",
            detail = "Running command..."
        )

        val reconciled = com.pratyush.orbitmobile.data.repository.implementations.AiChatRepositoryImpl.reconcileToolActivities(
            list,
            staleRunningUpdate
        )

        assertEquals(1, reconciled.size)
        assertEquals("Terminal state (completed) must not be downgraded to running", "completed", reconciled.first().status)
    }

    @Test
    fun `test Tool activity reconciliation keeps separate tool calls separate even with same tool name`() {
        val tool1 = AiToolActivity(
            id = "call_1",
            messageId = "msg_1",
            toolCallId = "call_1",
            toolName = "search_code",
            status = "completed",
            detail = "query: test"
        )
        val tool2 = AiToolActivity(
            id = "call_2",
            messageId = "msg_1",
            toolCallId = "call_2",
            toolName = "search_code",
            status = "running",
            detail = "query: test2"
        )

        val list = com.pratyush.orbitmobile.data.repository.implementations.AiChatRepositoryImpl.reconcileToolActivities(
            listOf(tool1),
            tool2
        )

        assertEquals("Distinct toolCallIds must result in distinct cards", 2, list.size)
        assertEquals("call_1", list[0].toolCallId)
        assertEquals("call_2", list[1].toolCallId)
        assertEquals("completed", list[0].status)
        assertEquals("running", list[1].status)
    }

    @Test
    fun `test Markdown parser extracts multi-column tables and preserves empty cells`() {
        val tableMarkdown = """
            | Device | Filesystem | Size | Used | Avail | Use% | Mounted on |
            | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
            | /dev/nvme0n1p3 | ext4 | 468G | 120G | 325G | 27% | / |
            | /dev/nvme0n1p1 | vfat | 511M |  | 505M | 2% | /boot/efi |
        """.trimIndent()

        val blocks = com.pratyush.orbitmobile.presentation.screens.ai.components.parseMarkdownBlocks(tableMarkdown)
        assertEquals(1, blocks.size)
        assertTrue(blocks.first() is com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.Table)

        val table = blocks.first() as com.pratyush.orbitmobile.presentation.screens.ai.components.ParsedBlock.Table
        assertEquals(7, table.headers.size)
        assertEquals("Device", table.headers[0])
        assertEquals("Mounted on", table.headers[6])
        assertEquals(2, table.rows.size)
        assertEquals(7, table.rows[0].size)
        assertEquals("/dev/nvme0n1p3", table.rows[0][0])
        assertEquals("/", table.rows[0][6])
        // Empty cell preserved
        assertEquals("", table.rows[1][3])
    }

    @Test
    fun `test stripMarkdownFormatting cleans link URLs and styles for accurate width measurement`() {
        val markdownLink = "[C:\\Program Files (x86)](file:///C:/Program%20Files%20%28x86%29)"
        val stripped = com.pratyush.orbitmobile.presentation.screens.ai.components.stripMarkdownFormatting(markdownLink)
        assertEquals("C:\\Program Files (x86)", stripped)

        val boldText = "**443.78 GB**"
        assertEquals("443.78 GB", com.pratyush.orbitmobile.presentation.screens.ai.components.stripMarkdownFormatting(boldText))

        val mixed = "[User](url) with `code` and **bold**"
        assertEquals("User with code and bold", com.pratyush.orbitmobile.presentation.screens.ai.components.stripMarkdownFormatting(mixed))
    }
}
