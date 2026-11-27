package com.pratyush.orbitmobile.presentation.screens.ai.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import org.json.JSONObject

/**
 * Rich Native Compose Markdown Renderer for ORBIT Mobile AI.
 * Parses and formats Antigravity AI responses with proper typography,
 * code blocks, headings, bullet/numbered lists, quotes, tables, and links.
 */
@Composable
fun AiMarkdownRenderer(
    content: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cleanContent = remember(content) { sanitizeRawContent(content) }
    val blocks = remember(cleanContent) { parseMarkdownBlocks(cleanContent) }

    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex

            when (block) {
                is ParsedBlock.Heading -> {
                    HeadingBlock(block = block)
                }

                is ParsedBlock.Code -> {
                    AiCodeBlock(
                        code = block.code,
                        language = block.language
                    )
                }

                is ParsedBlock.BlockQuote -> {
                    BlockQuoteView(quote = block.text)
                }

                is ParsedBlock.UnorderedList -> {
                    UnorderedListView(items = block.items)
                }

                is ParsedBlock.OrderedList -> {
                    OrderedListView(items = block.items)
                }

                is ParsedBlock.Table -> {
                    TableView(table = block)
                }

                is ParsedBlock.HorizontalRule -> {
                    HorizontalDivider(
                        color = Color(0xFF28283C),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                is ParsedBlock.Paragraph -> {
                    ParagraphView(
                        text = block.text,
                        showCursor = isStreaming && isLastBlock,
                        cursorAlpha = cursorAlpha
                    )
                }
            }
        }

        if (blocks.isEmpty() && isStreaming) {
            Box(
                modifier = Modifier
                    .size(7.dp, 16.dp)
                    .alpha(cursorAlpha)
                    .background(OrbitColors.PurpleAccentGlow)
            )
        }
    }
}

@Composable
private fun HeadingBlock(block: ParsedBlock.Heading) {
    val (style, color) = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        ) to OrbitColors.PurpleAccentGlow
        2 -> MaterialTheme.typography.titleMedium.copy(
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF1F5F9)
        ) to OrbitColors.PurpleSecondary
        else -> MaterialTheme.typography.titleSmall.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFCBD5E1)
        ) to OrbitColors.PurpleSecondary
    }

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        val annotated = remember(block.text) { formatInlineMarkdown(block.text) }
        Text(text = annotated, style = style)
    }
}

@Composable
private fun ParagraphView(
    text: String,
    showCursor: Boolean,
    cursorAlpha: Float
) {
    val context = LocalContext.current
    val annotated = remember(text) { formatInlineMarkdown(text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFFE2E8F0),
                lineHeight = 22.sp,
                fontSize = 13.5.sp
            ),
            modifier = Modifier.weight(1f, fill = false),
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
            }
        )

        if (showCursor) {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp, bottom = 2.dp)
                    .size(6.dp, 14.dp)
                    .alpha(cursorAlpha)
                    .background(OrbitColors.PurpleAccentGlow)
            )
        }
    }
}

@Composable
private fun BlockQuoteView(quote: String) {
    val annotated = remember(quote) { formatInlineMarkdown(quote) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141420))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(OrbitColors.PurplePrimary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = FontStyle.Italic,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )
        )
    }
}

@Composable
private fun UnorderedListView(items: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OrbitColors.PurpleSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.padding(end = 8.dp, start = 4.dp)
                )
                val annotated = remember(item) { formatInlineMarkdown(item) }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFE2E8F0),
                        lineHeight = 20.sp,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OrderedListView(items: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (indexStr, item) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "$indexStr.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OrbitColors.PurpleSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(end = 6.dp, start = 2.dp)
                )
                val annotated = remember(item) { formatInlineMarkdown(item) }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFE2E8F0),
                        lineHeight = 20.sp,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TableView(table: ParsedBlock.Table) {
    val scrollState = rememberScrollState()

    val numColumns = remember(table) {
        maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    }

    if (numColumns == 0) return

    val rawColWidths = remember(table, numColumns) {
        (0 until numColumns).map { colIdx ->
            val hLen = stripMarkdownFormatting(table.headers.getOrNull(colIdx) ?: "").length
            val maxCellLen = table.rows.maxOfOrNull {
                stripMarkdownFormatting(it.getOrNull(colIdx) ?: "").length
            } ?: 0
            val maxLen = maxOf(hLen, maxCellLen)
            // Minimum 75.dp for short metric/status columns, expanding up to 260.dp based on clean text length
            maxOf(75.dp, minOf(260.dp, (maxLen * 7.5).dp + 22.dp))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F0F16))
            .border(1.dp, Color(0xFF242436), RoundedCornerShape(8.dp))
    ) {
        val availableWidth = maxWidth - 16.dp // accounting for horizontal padding
        val totalRawWidth = remember(rawColWidths) { rawColWidths.fold(0.dp) { acc, w -> acc + w } }

        // If table fits within available screen width, expand columns proportionally so it fits naturally without scrollbars
        val finalColWidths = remember(rawColWidths, availableWidth, totalRawWidth) {
            if (availableWidth > totalRawWidth && totalRawWidth > 0.dp) {
                val scale = availableWidth / totalRawWidth
                rawColWidths.map { it * scale }
            } else {
                rawColWidths
            }
        }

        val tableTotalWidth = remember(finalColWidths) { finalColWidths.fold(0.dp) { acc, w -> acc + w } }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier.width(tableTotalWidth)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF181826), RoundedCornerShape(4.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        finalColWidths.forEachIndexed { colIdx, width ->
                            val h = table.headers.getOrNull(colIdx)?.trim() ?: ""
                            Text(
                                text = h,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = OrbitColors.PurpleSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier
                                    .width(width)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Rows
                    table.rows.forEachIndexed { rIdx, row ->
                        val bg = if (rIdx % 2 == 1) Color(0xFF141420) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg, RoundedCornerShape(3.dp))
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            finalColWidths.forEachIndexed { colIdx, width ->
                                val cell = row.getOrNull(colIdx)?.trim() ?: ""
                                val annotated = remember(cell) { formatInlineMarkdown(cell) }
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier
                                        .width(width)
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Sleek glowing scroll indicator when table extends beyond viewport
            if (scrollState.maxValue > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color(0xFF1E1E2C), RoundedCornerShape(1.5.dp))
                ) {
                    val maxScroll = scrollState.maxValue.toFloat()
                    val scrollFraction = if (maxScroll > 0f) scrollState.value.toFloat() / maxScroll else 0f
                    val indicatorWidthFraction = (availableWidth / tableTotalWidth).coerceIn(0.15f, 0.85f)
                    val indicatorWidth = availableWidth * indicatorWidthFraction
                    val maxTravel = availableWidth - indicatorWidth
                    val currentOffset = maxTravel * scrollFraction

                    Box(
                        modifier = Modifier
                            .offset(x = currentOffset)
                            .width(indicatorWidth)
                            .height(2.5.dp)
                            .background(OrbitColors.PurpleAccentGlow.copy(alpha = 0.7f), RoundedCornerShape(1.5.dp))
                    )
                }
            }
        }
    }
}

/**
 * Strips markdown markup to calculate visible character length for column layout.
 */
fun stripMarkdownFormatting(input: String): String {
    if (input.isBlank()) return ""
    return input
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("__([^_]+)__"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("~~([^~]+)~~"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("_([^_]+)_"), "$1")
        .trim()
}

/**
 * Sanitizes input to prevent raw transport JSON or envelope strings from rendering into the chat bubble.
 */
fun sanitizeRawContent(input: String): String {
    if (input.isBlank()) return ""

    var text = input.trim()

    // Filter raw sessionUpdate JSON envelopes if any escaped through transport
    if (text.startsWith("{") && text.endsWith("}")) {
        try {
            val json = JSONObject(text)
            if (json.has("sessionUpdate")) {
                val update = json.optString("sessionUpdate")
                if (update == "agent_message_chunk" && json.has("content")) {
                    val cObj = json.optJSONObject("content")
                    if (cObj != null && cObj.has("text")) {
                        return cObj.optString("text")
                    }
                }
            }
        } catch (_: Exception) { }
    }

    return text
}

/**
 * Parses markdown into structured semantic blocks.
 */
sealed class ParsedBlock {
    data class Heading(val level: Int, val text: String) : ParsedBlock()
    data class Code(val code: String, val language: String) : ParsedBlock()
    data class BlockQuote(val text: String) : ParsedBlock()
    data class UnorderedList(val items: List<String>) : ParsedBlock()
    data class OrderedList(val items: List<Pair<String, String>>) : ParsedBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ParsedBlock()
    data object HorizontalRule : ParsedBlock()
    data class Paragraph(val text: String) : ParsedBlock()
}

fun parseMarkdownBlocks(input: String): List<ParsedBlock> {
    if (input.isBlank()) return emptyList()

    val blocks = mutableListOf<ParsedBlock>()
    val lines = input.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Fenced Code Block: ```lang
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // Skip closing ```
            blocks.add(ParsedBlock.Code(codeLines.joinToString("\n"), lang))
            continue
        }

        // 2. Horizontal Rule: ---, ***, ___
        val trimmed = line.trim()
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(ParsedBlock.HorizontalRule)
            i++
            continue
        }

        // 3. Headings: #, ##, ###
        if (trimmed.startsWith("#")) {
            val match = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
            if (match != null) {
                val level = match.groupValues[1].length
                val hText = match.groupValues[2]
                blocks.add(ParsedBlock.Heading(level, hText))
                i++
                continue
            }
        }

        // 4. Blockquotes: > Quote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(ParsedBlock.BlockQuote(quoteLines.joinToString(" ")))
            continue
        }

        // 5. Unordered List: - or *
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val listItems = mutableListOf<String>()
            while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                val item = lines[i].trim().substring(2).trim()
                listItems.add(item)
                i++
            }
            blocks.add(ParsedBlock.UnorderedList(listItems))
            continue
        }

        // 6. Ordered List: 1. Item
        val orderedMatch = Regex("^(\\d+)[.)]\\s+(.*)$").find(trimmed)
        if (orderedMatch != null) {
            val listItems = mutableListOf<Pair<String, String>>()
            while (i < lines.size) {
                val currentTrimmed = lines[i].trim()
                val m = Regex("^(\\d+)[.)]\\s+(.*)$").find(currentTrimmed) ?: break
                listItems.add(m.groupValues[1] to m.groupValues[2])
                i++
            }
            blocks.add(ParsedBlock.OrderedList(listItems))
            continue
        }

        // 7. Table: | Col 1 | Col 2 |
        if (trimmed.startsWith("|") && trimmed.contains("|")) {
            val delimiterLine = if (i + 1 < lines.size) lines[i + 1].trim() else ""
            val isTableDelimiter = delimiterLine.startsWith("|") && delimiterLine.contains("---")
            if (isTableDelimiter) {
                val headerCells = trimmed.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                i += 2 // skip header and delimiter line
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val rowLine = lines[i].trim()
                    if (!rowLine.startsWith("|") || !rowLine.contains("|")) break
                    val rowCells = rowLine.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                    tableRows.add(rowCells)
                    i++
                }
                blocks.add(ParsedBlock.Table(headerCells, tableRows))
                continue
            }
        }

        // 8. Skip blank lines
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 9. Normal paragraph: gather consecutive text lines
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val pLine = lines[i]
            val pTrimmed = pLine.trim()
            if (pTrimmed.isEmpty() ||
                pTrimmed.startsWith("```") ||
                pTrimmed.startsWith("#") ||
                pTrimmed.startsWith(">") ||
                pTrimmed.startsWith("- ") ||
                pTrimmed.startsWith("* ") ||
                Regex("^(\\d+)[.)]\\s+").containsMatchIn(pTrimmed) ||
                (pTrimmed.startsWith("|") && pTrimmed.endsWith("|")) ||
                pTrimmed == "---"
            ) {
                break
            }
            paragraphLines.add(pLine)
            i++
        }

        if (paragraphLines.isNotEmpty()) {
            blocks.add(ParsedBlock.Paragraph(paragraphLines.joinToString("\n")))
        }
    }

    return blocks
}

/**
 * Formats inline markdown tokens:
 * - **bold** or __bold__
 * - *italic* or _italic_
 * - `code` (inline monospace code)
 * - [label](url) (clickable hyperlink)
 * - ~~strikethrough~~
 */
fun formatInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        // Regex tokenizing inline patterns
        val tokenRegex = Regex(
            "(\\*\\*([^*]+)\\*\\*)|" +         // 1,2: **bold**
            "(__([^_]+)__)|" +                 // 3,4: __bold__
            "(`([^`]+)`)|" +                   // 5,6: `code`
            "(\\[([^\\]]+)\\]\\(([^)]+)\\))|" + // 7,8,9: [text](url)
            "(~~([^~]+)~~)|" +                 // 10,11: ~~strike~~
            "(\\*([^*]+)\\*)|" +               // 12,13: *italic*
            "(_([^_]+)_)"                      // 14,15: _italic_
        )

        var lastIdx = 0
        val matches = tokenRegex.findAll(text)

        for (match in matches) {
            if (match.range.first > lastIdx) {
                append(text.substring(lastIdx, match.range.first))
            }

            when {
                // **bold**
                match.groupValues[2].isNotEmpty() -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                    append(match.groupValues[2])
                    pop()
                }
                // __bold__
                match.groupValues[4].isNotEmpty() -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                    append(match.groupValues[4])
                    pop()
                }
                // `inline code`
                match.groupValues[6].isNotEmpty() -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF222234),
                            color = OrbitColors.PurpleSecondary,
                            fontSize = 12.sp
                        )
                    )
                    append(" ${match.groupValues[6]} ")
                    pop()
                }
                // [link](url)
                match.groupValues[8].isNotEmpty() -> {
                    val label = match.groupValues[8]
                    val url = match.groupValues[9]
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(
                        SpanStyle(
                            color = OrbitColors.StatusInfo,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(label)
                    pop()
                    pop()
                }
                // ~~strike~~
                match.groupValues[11].isNotEmpty() -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color(0xFF64748B)))
                    append(match.groupValues[11])
                    pop()
                }
                // *italic*
                match.groupValues[13].isNotEmpty() -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFFE2E8F0)))
                    append(match.groupValues[13])
                    pop()
                }
                // _italic_
                match.groupValues[15].isNotEmpty() -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFFE2E8F0)))
                    append(match.groupValues[15])
                    pop()
                }
                else -> append(match.value)
            }

            lastIdx = match.range.last + 1
        }

        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}
