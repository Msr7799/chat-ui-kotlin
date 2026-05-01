package com.example.chat_ui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.chat_ui.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Full-featured Markdown renderer matching the JavaScript implementation:
 * - Think blocks (<think>...</think>) with collapsible UI
 * - Code blocks (```language\n...\n```) with syntax highlighting
 * - Inline code (`code`)
 * - Bold (**text** or __text__)
 * - Italic (*text* or _text_)
 * - Bold+Italic (***text***)
 * - Strikethrough (~~text~~)
 * - Headings (# ## ###)
 * - Lists (- or * or numbered)
 * - Blockquotes (> text)
 * - Links [text](url)
 * - Math blocks ($$...$$)
 */

data class MarkdownBlock(
    val type: BlockType,
    val content: String,
    val language: String = "",
    val isClosed: Boolean = true
)

enum class BlockType {
    TEXT,
    CODE,
    THINK,
    TOOL_CALL,
    SEARCH_RESULTS,
    HEADING1,
    HEADING2,
    HEADING3,
    LIST_ITEM,
    QUOTE,
    MATH,
    TABLE,
    SVG_IMAGE
}

/**
 * Parse markdown content into blocks - handles think blocks, code blocks, and text
 * Similar to parseBlocks.ts from the JavaScript implementation
 */
fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var remaining = normalizeBlockquotedCodeFences(content)
    
    // Patterns for block detection
    val thinkOpenTag = "<think>"
    val thinkCloseTag = "</think>"
    val toolCallOpenTag = "<tool_call>"
    val toolCallCloseTag = "</tool_call>"
    val searchResultsOpenTag = "<search_results>"
    val searchResultsCloseTag = "</search_results>"
    val codeBlockDelimiter = "```"
    val mathBlockDelimiter = "$$"
    
    while (remaining.isNotEmpty()) {
        // Find the earliest special block
        val thinkStart = remaining.indexOf(thinkOpenTag).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val toolCallStart = remaining.indexOf(toolCallOpenTag).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val searchResultsStart = remaining.indexOf(searchResultsOpenTag).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val codeStart = remaining.indexOf(codeBlockDelimiter).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val mathStart = remaining.indexOf(mathBlockDelimiter).takeIf { it >= 0 } ?: Int.MAX_VALUE
        
        val earliest = minOf(thinkStart, toolCallStart, searchResultsStart, codeStart, mathStart)
        
        // If no special blocks found, add remaining as text
        if (earliest == Int.MAX_VALUE) {
            if (remaining.isNotBlank()) {
                blocks.add(MarkdownBlock(BlockType.TEXT, remaining))
            }
            break
        }
        
        // Add any text before the special block
        if (earliest > 0) {
            val textBefore = remaining.substring(0, earliest)
            if (textBefore.isNotBlank()) {
                blocks.add(MarkdownBlock(BlockType.TEXT, textBefore))
            }
            remaining = remaining.substring(earliest)
        }
        
        // Handle think block
        if (remaining.startsWith(thinkOpenTag)) {
            val closeIndex = remaining.indexOf(thinkCloseTag, thinkOpenTag.length)
            if (closeIndex >= 0) {
                // Closed think block
                val thinkContent = remaining.substring(thinkOpenTag.length, closeIndex)
                blocks.add(MarkdownBlock(
                    type = BlockType.THINK,
                    content = thinkContent.trim(),
                    isClosed = true
                ))
                remaining = remaining.substring(closeIndex + thinkCloseTag.length)
            } else {
                // Unclosed think block (streaming)
                val thinkContent = remaining.substring(thinkOpenTag.length)
                blocks.add(MarkdownBlock(
                    type = BlockType.THINK,
                    content = thinkContent.trim(),
                    isClosed = false
                ))
                remaining = ""
            }
            continue
        }
        
        // Handle tool_call block
        if (remaining.startsWith(toolCallOpenTag)) {
            val closeIndex = remaining.indexOf(toolCallCloseTag, toolCallOpenTag.length)
            if (closeIndex >= 0) {
                // Closed tool_call block
                val toolCallContent = remaining.substring(toolCallOpenTag.length, closeIndex)
                blocks.add(MarkdownBlock(
                    type = BlockType.TOOL_CALL,
                    content = toolCallContent.trim(),
                    isClosed = true
                ))
                remaining = remaining.substring(closeIndex + toolCallCloseTag.length)
            } else {
                // Unclosed tool_call block (streaming)
                val toolCallContent = remaining.substring(toolCallOpenTag.length)
                blocks.add(MarkdownBlock(
                    type = BlockType.TOOL_CALL,
                    content = toolCallContent.trim(),
                    isClosed = false
                ))
                remaining = ""
            }
            continue
        }
        
        // Handle search_results block
        if (remaining.startsWith(searchResultsOpenTag)) {
            val closeIndex = remaining.indexOf(searchResultsCloseTag, searchResultsOpenTag.length)
            if (closeIndex >= 0) {
                // Closed search_results block
                val searchContent = remaining.substring(searchResultsOpenTag.length, closeIndex)
                blocks.add(MarkdownBlock(
                    type = BlockType.SEARCH_RESULTS,
                    content = searchContent.trim(),
                    isClosed = true
                ))
                remaining = remaining.substring(closeIndex + searchResultsCloseTag.length)
            } else {
                // Unclosed search_results block (streaming)
                val searchContent = remaining.substring(searchResultsOpenTag.length)
                blocks.add(MarkdownBlock(
                    type = BlockType.SEARCH_RESULTS,
                    content = searchContent.trim(),
                    isClosed = false
                ))
                remaining = ""
            }
            continue
        }
        
        // Handle math block ($$...$$)
        if (remaining.startsWith(mathBlockDelimiter)) {
            val secondDelimiter = remaining.indexOf(mathBlockDelimiter, 2)
            if (secondDelimiter >= 2) {
                val mathContent = remaining.substring(2, secondDelimiter)
                blocks.add(MarkdownBlock(
                    type = BlockType.MATH,
                    content = mathContent.trim(),
                    isClosed = true
                ))
                remaining = remaining.substring(secondDelimiter + 2)
            } else {
                // Unclosed math block
                val mathContent = remaining.substring(2)
                blocks.add(MarkdownBlock(
                    type = BlockType.MATH,
                    content = mathContent.trim(),
                    isClosed = false
                ))
                remaining = ""
            }
            continue
        }
        
        // Handle code block
        if (remaining.startsWith(codeBlockDelimiter)) {
            val firstNewline = remaining.indexOf('\n', 3)
            if (firstNewline >= 0) {
                val language = remaining.substring(3, firstNewline).trim()
                val afterLang = remaining.substring(firstNewline + 1)
                val closeIndex = afterLang.indexOf(codeBlockDelimiter)
                
                if (closeIndex >= 0) {
                    // Closed code block
                    val codeContent = afterLang.substring(0, closeIndex)
                    
                    // Check if this is SVG/XML code that should be rendered as image
                    if (language.lowercase() in listOf("svg", "xml") && isSvgCode(codeContent)) {
                        blocks.add(MarkdownBlock(
                            type = BlockType.SVG_IMAGE,
                            content = codeContent.trimEnd(),
                            language = language,
                            isClosed = true
                        ))
                    } else {
                        blocks.add(MarkdownBlock(
                            type = BlockType.CODE,
                            content = codeContent.trimEnd(),
                            language = language,
                            isClosed = true
                        ))
                    }
                    remaining = afterLang.substring(closeIndex + 3)
                } else {
                    // Unclosed code block (streaming)
                    blocks.add(MarkdownBlock(
                        type = BlockType.CODE,
                        content = afterLang,
                        language = language,
                        isClosed = false
                    ))
                    remaining = ""
                }
            } else {
                // Just ``` with no newline yet (streaming)
                val lang = remaining.substring(3)
                blocks.add(MarkdownBlock(
                    type = BlockType.CODE,
                    content = "",
                    language = lang,
                    isClosed = false
                ))
                remaining = ""
            }
            continue
        }
        
        // Safety: skip one character if nothing matched (shouldn't happen)
        if (remaining.isNotEmpty()) {
            blocks.add(MarkdownBlock(BlockType.TEXT, remaining.take(1)))
            remaining = remaining.drop(1)
        }
    }
    
    return blocks
}

private fun normalizeBlockquotedCodeFences(content: String): String {
    val lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val normalized = mutableListOf<String>()
    var insideQuotedCode = false

    for (line in lines) {
        val unquoted = line.removeBlockquotePrefix()
        val isQuotedFence = line.trimStart().startsWith(">") && unquoted.trimStart().startsWith("```")

        if (isQuotedFence) {
            normalized.add(unquoted)
            insideQuotedCode = !insideQuotedCode
        } else if (insideQuotedCode && line.trimStart().startsWith(">")) {
            normalized.add(unquoted)
        } else {
            normalized.add(line)
        }
    }

    return normalized.joinToString("\n")
}

private fun String.removeBlockquotePrefix(): String {
    val leadingSpaces = takeWhile { it == ' ' || it == '\t' }
    val rest = drop(leadingSpaces.length)
    if (!rest.startsWith(">")) return this
    return leadingSpaces + rest.drop(1).removePrefix(" ")
}

/**
 * Check if code content is valid SVG
 */
private fun isSvgCode(code: String): Boolean {
    val trimmed = code.trim()
    // Check if it starts with SVG tag or XML declaration followed by SVG
    return trimmed.contains("<svg", ignoreCase = true) && 
           (trimmed.startsWith("<svg", ignoreCase = true) || 
            trimmed.startsWith("<?xml", ignoreCase = true))
}

/**
 * Detect if text contains a markdown table
 * Table format: | col1 | col2 |
 *               |------|------|
 *               | val1 | val2 |
 */
private fun detectTable(text: String): Pair<String?, String> {
    val lines = text.split("\n")
    var tableLines = mutableListOf<String>()
    var foundTable = false
    var remainingText = ""
    
    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trim()
        
        // Check if line looks like a table row (starts and ends with |)
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            tableLines.add(line)
            foundTable = true
        } else if (foundTable) {
            // Table ended, collect remaining text
            remainingText = lines.drop(index).joinToString("\n")
            break
        }
    }
    
    return if (tableLines.size >= 2) { // At least header + separator
        Pair(tableLines.joinToString("\n"), remainingText)
    } else {
        Pair(null, text)
    }
}

@Composable
fun MarkdownRenderer(
    content: String,
    isUser: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    themeColors: ThemeColors? = null
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            
            when (block.type) {
                BlockType.THINK -> {
                    ThinkBlock(
                        content = block.content,
                        isLoading = isLoading && isLastBlock && !block.isClosed,
                        isClosed = block.isClosed
                    )
                }
                BlockType.TOOL_CALL -> {
                    ToolCallBlock(
                        content = block.content,
                        isLoading = isLoading && isLastBlock && !block.isClosed,
                        isClosed = block.isClosed
                    )
                }
                BlockType.SEARCH_RESULTS -> {
                    SearchResultsBlock(
                        content = block.content,
                        isLoading = isLoading && isLastBlock && !block.isClosed,
                        isClosed = block.isClosed
                    )
                }
                BlockType.CODE -> {
                    val blockIsLoading = isLoading && isLastBlock && !block.isClosed
                    if (block.content.isNotBlank() || blockIsLoading) {
                        CodeBlockRenderer(
                            code = block.content,
                            language = block.language,
                            isClosed = block.isClosed,
                            isLoading = blockIsLoading
                        )
                    }
                }
                BlockType.MATH -> {
                    MathBlockRenderer(
                        content = block.content,
                        isClosed = block.isClosed
                    )
                }
                BlockType.TABLE -> {
                    TableRenderer(
                        content = block.content
                    )
                }
                BlockType.SVG_IMAGE -> {
                    SvgImageDisplay(
                        svgCode = block.content
                    )
                }
                BlockType.TEXT -> {
                    RichTextRenderer(
                        text = block.content,
                        isUser = isUser,
                        themeColors = themeColors
                    )
                }
                else -> {
                    Text(
                        text = block.content,
                        color = if (isUser) (themeColors?.userBubbleText ?: Color(0xFF1a1a1a)) 
                               else (themeColors?.assistantBubbleText ?: TextPrimary),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

/**
 * Math block renderer - displays LaTeX-style math expressions
 */
@Composable
fun MathBlockRenderer(
    content: String,
    isClosed: Boolean = true
) {
    if (content.isBlank() && isClosed) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.weight(1f, fill = false)
            )
            // Show streaming indicator if not closed
            if (!isClosed) {
                Spacer(modifier = Modifier.width(8.dp))
                LoadingDots()
            }
        }
    }
}

/**
 * Table renderer - displays markdown tables with borders
 * Supports: | Header 1 | Header 2 |
 *           |----------|----------|
 *           | Cell 1   | Cell 2   |
 */
@Composable
fun TableRenderer(
    content: String,
    modifier: Modifier = Modifier
) {
    val lines = content.trim().split("\n").filter { it.isNotBlank() }
    if (lines.isEmpty()) return
    
    // Parse table structure
    val rows = lines.map { line ->
        line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }
    }
    
    // Skip separator line (usually second line with dashes)
    val headerRow = rows.firstOrNull() ?: return
    val dataRows = rows.drop(1).filter { row ->
        !row.all { cell -> cell.matches(Regex("^[-:]+$")) }
    }
    
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dataRows.forEach { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        headerRow.forEachIndexed { index, header ->
                            val value = row.getOrNull(index).orEmpty()
                            if (header.isNotBlank() || value.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = header,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(0.38f)
                                    )
                                    Text(
                                        text = value,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.weight(0.62f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    headerRow.forEach { cell ->
                        Text(
                            text = cell,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                dataRows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (rowIndex % 2 == 0) Color.Transparent
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        headerRow.indices.forEach { index ->
                            Text(
                                text = row.getOrNull(index).orEmpty(),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Think block component - collapsible reasoning display
 * Matches OpenReasoningResults.svelte behavior:
 * - Auto-expand on loading start
 * - Auto-collapse on loading finish
 * - Renders markdown content when expanded
 * - Shows stripped text preview when collapsed
 */
@Composable
fun ThinkBlock(
    content: String,
    isLoading: Boolean = false,
    isClosed: Boolean = true
) {
    // Match OpenReasoningResults.svelte: start closed, auto-expand on loading, auto-collapse when done
    // Use content hash as key to properly track state per think block
    var isExpanded by remember { mutableStateOf(!isClosed) }
    var wasClosed by remember { mutableStateOf(isClosed) }
    
    // Track isClosed transitions to auto-collapse when think block closes
    LaunchedEffect(isClosed) {
        if (isClosed && !wasClosed) {
            // Think block just closed - auto-collapse
            isExpanded = false
        } else if (!isClosed && wasClosed) {
            // Think block just opened (streaming started) - auto-expand
            isExpanded = true
        }
        wasClosed = isClosed
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF374151).copy(alpha = 0.6f))
    ) {
        // Header - clickable to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Brain/Tree icon similar to Svelte version
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = "Thinking",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isLoading) "Thinking..." else "Reasoning",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isLoading) {
                    LoadingDots()
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Expanded: show full content with markdown rendering (like Svelte version)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                // Use RichTextRenderer for markdown inside think blocks
                ThinkContentRenderer(
                    content = content,
                    isLoading = isLoading
                )
            }
        }
        
        // Collapsed: 2-line preview with stripped markdown (matching Svelte)
        AnimatedVisibility(
            visible = !isExpanded && content.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = stripMarkdown(content),
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                maxLines = 2,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

/**
 * Strip markdown formatting for preview text (matching Svelte: replace(/[#*_`~[\]]/g, ""))
 */
private fun stripMarkdown(text: String): String {
    return text
        .replace(Regex("[#*_`~\\[\\]]"), "")
        .replace(Regex("\\n+"), " ")
        .trim()
        .take(150) + if (text.length > 150) "..." else ""
}

/**
 * Renders think block content with markdown formatting
 */
@Composable
private fun ThinkContentRenderer(
    content: String,
    isLoading: Boolean = false
) {
    val lines = content.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trimStart()
            when {
                // Heading
                trimmedLine.startsWith("# ") -> {
                    Text(
                        text = parseThinkInlineMarkdown(trimmedLine.removePrefix("# ")),
                        color = Color(0xFFD1D5DB),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                }
                trimmedLine.startsWith("## ") || trimmedLine.startsWith("### ") -> {
                    val prefix = if (trimmedLine.startsWith("### ")) "### " else "## "
                    Text(
                        text = parseThinkInlineMarkdown(trimmedLine.removePrefix(prefix)),
                        color = Color(0xFFD1D5DB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
                // List items
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "•",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp
                        )
                        Text(
                            text = parseThinkInlineMarkdown(
                                trimmedLine.removePrefix("- ").removePrefix("* ")
                            ),
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                // Numbered list
                trimmedLine.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val number = trimmedLine.takeWhile { it.isDigit() }
                    val listContent = trimmedLine.removePrefix("$number. ")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "$number.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp
                        )
                        Text(
                            text = parseThinkInlineMarkdown(listContent),
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                // Regular text
                line.isNotBlank() -> {
                    Text(
                        text = parseThinkInlineMarkdown(line),
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
                // Empty line = paragraph break
                line.isBlank() && index > 0 && index < lines.size - 1 -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        
        // Show loading cursor at end if still streaming
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "▊",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Parse inline markdown for think block content (bold, italic, code)
 */
@Composable
private fun parseThinkInlineMarkdown(text: String): AnnotatedString {
    val textColor = Color(0xFF9CA3AF)
    val codeColor = Color(0xFF60A5FA)
    val codeBgColor = Color(0xFF60A5FA).copy(alpha = 0.15f)
    
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val remaining = text.substring(i)
            
            when {
                // Bold with **
                remaining.startsWith("**") -> {
                    val endIndex = text.indexOf("**", i + 2)
                    if (endIndex > i) {
                        val content = text.substring(i + 2, endIndex)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFD1D5DB))) {
                            append(content)
                        }
                        i = endIndex + 2
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Inline code with `
                text[i] == '`' -> {
                    val endIndex = text.indexOf('`', i + 1)
                    if (endIndex > i) {
                        val content = text.substring(i + 1, endIndex)
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBgColor,
                            fontSize = 12.sp
                        )) {
                            append(" $content ")
                        }
                        i = endIndex + 1
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Italic with *
                text[i] == '*' && !remaining.startsWith("**") -> {
                    var endIndex = -1
                    var j = i + 1
                    while (j < text.length) {
                        if (text[j] == '*' && (j + 1 >= text.length || text[j + 1] != '*')) {
                            endIndex = j
                            break
                        }
                        j++
                    }
                    if (endIndex > i + 1) {
                        val content = text.substring(i + 1, endIndex)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                            append(content)
                        }
                        i = endIndex + 1
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                else -> {
                    withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                    i++
                }
            }
        }
    }
}

/**
 * Loading dots animation with pulsing effect
 */
@Composable
private fun LoadingDots() {
    var dotIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            dotIndex = (dotIndex + 1) % 3
        }
    }
    
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by animateFloatAsState(
                targetValue = if (index == dotIndex) 1f else 0.4f,
                animationSpec = tween(durationMillis = 200),
                label = "dot_alpha_$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9CA3AF).copy(alpha = alpha))
            )
        }
    }
}

/**
 * Code block with syntax highlighting colors and copy button
 * Similar to CodeBlock.svelte from the web implementation
 */
@Composable
fun CodeBlockRenderer(
    code: String,
    language: String,
    isClosed: Boolean = true,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var showHtmlPreview by remember { mutableStateOf(false) }
    val displayCode = remember(code) { trimBlankCodeEdges(code) }
    
    // Check if this is previewable HTML
    val isHtml = language.lowercase() in listOf("html", "htm", "xml", "svg")
    
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E2E)) // Dark background like VS Code
    ) {
        // Header with language and copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D3D))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Language indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(getLanguageColor(language))
                )
                Text(
                    text = language.ifEmpty { "plaintext" },
                    color = Color(0xFFADB5BD),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                // Show streaming indicator if code block is not closed or still loading
                if (!isClosed || isLoading) {
                    LoadingDots()
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Preview button (only for HTML/XML/SVG)
                if (isHtml) {
                    IconButton(
                        onClick = { showHtmlPreview = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "Preview HTML",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Copy button
                IconButton(
                    onClick = { 
                        copyToClipboard(context, displayCode)
                        copied = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "Copied!" else "Copy code",
                        tint = if (copied) Color(0xFF4ADE80) else Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // HTML Preview Modal
        if (isHtml) {
            HtmlPreviewModal(
                isOpen = showHtmlPreview,
                htmlContent = code,
                title = "HTML Preview",
                onClose = { showHtmlPreview = false }
            )
        }
        
        if (displayCode.isNotBlank()) {
            // Code content with syntax highlighting - scrollable horizontally
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightCode(displayCode, language),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    softWrap = false, // Prevent text wrapping - allow horizontal scroll
                    maxLines = Int.MAX_VALUE
                )
            }
        }
    }
}

private fun trimBlankCodeEdges(code: String): String {
    val lines = code.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val firstContent = lines.indexOfFirst { it.isNotBlank() }
    if (firstContent == -1) return ""
    val lastContent = lines.indexOfLast { it.isNotBlank() }
    return lines.subList(firstContent, lastContent + 1).joinToString("\n")
}

/**
 * Get a color for the language indicator dot
 */
private fun getLanguageColor(language: String): Color {
    return when (language.lowercase()) {
        "kotlin" -> Color(0xFFA97BFF)
        "java" -> Color(0xFFE76F00)
        "javascript", "js" -> Color(0xFFF7DF1E)
        "typescript", "ts" -> Color(0xFF3178C6)
        "python", "py" -> Color(0xFF3776AB)
        "go" -> Color(0xFF00ADD8)
        "rust" -> Color(0xFFDEA584)
        "c" -> Color(0xFF555555)
        "cpp", "c++" -> Color(0xFF00599C)
        "csharp", "c#" -> Color(0xFF239120)
        "swift" -> Color(0xFFFA7343)
        "ruby" -> Color(0xFFCC342D)
        "php" -> Color(0xFF777BB4)
        "html" -> Color(0xFFE34F26)
        "css" -> Color(0xFF1572B6)
        "json" -> Color(0xFF000000)
        "yaml", "yml" -> Color(0xFFCB171E)
        "bash", "shell", "sh" -> Color(0xFF4EAA25)
        "sql" -> Color(0xFFE38D00)
        "markdown", "md" -> Color(0xFF083FA1)
        else -> Color(0xFF6B7280)
    }
}

/**
 * Basic syntax highlighting for code
 */
@Composable
private fun highlightCode(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val keywords = when (language.lowercase()) {
            "kotlin", "java" -> listOf(
                "fun", "val", "var", "class", "object", "interface", "if", "else", "when",
                "for", "while", "return", "import", "package", "private", "public", "protected",
                "override", "suspend", "data", "sealed", "enum", "companion", "null", "true", "false"
            )
            "javascript", "typescript", "js", "ts" -> listOf(
                "function", "const", "let", "var", "class", "if", "else", "for", "while",
                "return", "import", "export", "default", "async", "await", "null", "undefined",
                "true", "false", "new", "this", "super", "extends", "implements"
            )
            "python", "py" -> listOf(
                "def", "class", "if", "elif", "else", "for", "while", "return", "import",
                "from", "as", "try", "except", "finally", "with", "lambda", "None", "True", "False"
            )
            else -> emptyList()
        }
        
        val stringColor = Color(0xFFA5D6FF) // Light blue for strings
        val keywordColor = Color(0xFFFF7B72) // Red for keywords
        val commentColor = Color(0xFF8B949E) // Gray for comments
        val numberColor = Color(0xFF79C0FF) // Blue for numbers
        val defaultColor = Color(0xFFE6EDF3) // Light gray for default
        
        val lines = code.split("\n")
        lines.forEachIndexed { lineIndex, line ->
            var i = 0
            while (i < line.length) {
                // Check for comments
                if (line.substring(i).startsWith("//") || line.substring(i).startsWith("#")) {
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line.substring(i))
                    }
                    break
                }
                
                // Check for strings
                if (line[i] == '"' || line[i] == '\'') {
                    val quote = line[i]
                    val endIndex = line.indexOf(quote, i + 1)
                    if (endIndex > i) {
                        withStyle(SpanStyle(color = stringColor)) {
                            append(line.substring(i, endIndex + 1))
                        }
                        i = endIndex + 1
                        continue
                    }
                }
                
                // Check for keywords
                var foundKeyword = false
                for (keyword in keywords) {
                    if (line.substring(i).startsWith(keyword)) {
                        val endPos = i + keyword.length
                        if (endPos >= line.length || !line[endPos].isLetterOrDigit()) {
                            val startPos = if (i > 0 && line[i - 1].isLetterOrDigit()) false else true
                            if (startPos) {
                                withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                                    append(keyword)
                                }
                                i = endPos
                                foundKeyword = true
                                break
                            }
                        }
                    }
                }
                if (foundKeyword) continue
                
                // Check for numbers
                if (line[i].isDigit()) {
                    val start = i
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) {
                        i++
                    }
                    withStyle(SpanStyle(color = numberColor)) {
                        append(line.substring(start, i))
                    }
                    continue
                }
                
                // Default character
                withStyle(SpanStyle(color = defaultColor)) {
                    append(line[i])
                }
                i++
            }
            
            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Rich text renderer for inline markdown
 * Properly handles headings, lists, blockquotes, and inline formatting
 */
@Composable
fun RichTextRenderer(
    text: String,
    isUser: Boolean = false,
    themeColors: ThemeColors? = null
) {
    val textColor = if (isUser) (themeColors?.userBubbleText ?: Color.White) 
                   else (themeColors?.assistantBubbleText ?: TextPrimary)
    
    // Check for tables first
    val (tableContent, remainingAfterTable) = detectTable(text)
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (tableContent != null) {
            // Render table
            TableRenderer(content = tableContent)
            
            // Render remaining text after table
            if (remainingAfterTable.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                RichTextRenderer(text = remainingAfterTable, isUser = isUser, themeColors = themeColors)
            }
            return@Column
        }
        
        val lines = text.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trimStart()
            
            when {
                // Heading 6 (######)
                trimmedLine.startsWith("###### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmedLine.removePrefix("###### "), isUser, themeColors),
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
                // Heading 5 (#####)
                trimmedLine.startsWith("##### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmedLine.removePrefix("##### "), isUser, themeColors),
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
                    )
                }
                // Heading 4 (####)
                trimmedLine.startsWith("#### ") -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = parseInlineMarkdown(trimmedLine.removePrefix("#### "), isUser, themeColors),
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // Heading 3 (###)
                trimmedLine.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = parseInlineMarkdown(trimmedLine.removePrefix("### "), isUser, themeColors),
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Heading 2 (##)
                trimmedLine.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = parseInlineMarkdown(trimmedLine.removePrefix("## "), isUser, themeColors),
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                // Heading 1 (#)
                trimmedLine.startsWith("# ") -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = parseInlineMarkdown(trimmedLine.removePrefix("# "), isUser, themeColors),
                        color = textColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // Unordered list (- or *)
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parseInlineMarkdown(
                                trimmedLine.removePrefix("- ").removePrefix("* "),
                                isUser,
                                themeColors
                            ),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                // Blockquote (>)
                trimmedLine.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .heightIn(min = 24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF6366F1))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = parseInlineMarkdown(trimmedLine.removePrefix("> "), isUser, themeColors),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF9CA3AF),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                // Ordered list (1. 2. etc.)
                trimmedLine.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val number = trimmedLine.takeWhile { it.isDigit() }
                    val content = trimmedLine.removePrefix("$number. ")
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$number.",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = parseInlineMarkdown(content, isUser, themeColors),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                // Horizontal rule (--- or ***)
                trimmedLine.matches(Regex("^[-*_]{3,}$")) -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF374151))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // Regular paragraph
                else -> {
                    if (line.isNotBlank()) {
                        Text(
                            text = parseInlineMarkdown(line, isUser, themeColors),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    } else if (i > 0 && i < lines.size - 1) {
                        // Empty line = paragraph break
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            i++
        }
    }
}

/**
 * Parse inline markdown (bold, italic, code, links, strikethrough)
 * Handles: **bold**, *italic*, ***bold+italic***, `code`, ~~strikethrough~~, [link](url)
 */
@Composable
private fun parseInlineMarkdown(text: String, isUser: Boolean, themeColors: ThemeColors? = null): AnnotatedString {
    val textColor = if (isUser) (themeColors?.userBubbleText ?: Color.White) else (themeColors?.assistantBubbleText ?: TextPrimary)
    val codeColor = if (isUser) (themeColors?.userBubbleText ?: Color.White) else Color(0xFF60A5FA)
    val codeBgColor = if (isUser) Color.White.copy(alpha = 0.15f) else Color(0xFF60A5FA).copy(alpha = 0.15f)
    val linkColor = Color(0xFF60A5FA)
    
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val remaining = text.substring(i)
            
            when {
                // Bold+Italic with ***
                remaining.startsWith("***") -> {
                    val endIndex = text.indexOf("***", i + 3)
                    if (endIndex > i) {
                        val content = text.substring(i + 3, endIndex)
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = textColor
                        )) {
                            append(content)
                        }
                        i = endIndex + 3
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Bold with **
                remaining.startsWith("**") -> {
                    val endIndex = text.indexOf("**", i + 2)
                    if (endIndex > i) {
                        val content = text.substring(i + 2, endIndex)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                            append(content)
                        }
                        i = endIndex + 2
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Bold with __
                remaining.startsWith("__") -> {
                    val endIndex = text.indexOf("__", i + 2)
                    if (endIndex > i) {
                        val content = text.substring(i + 2, endIndex)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                            append(content)
                        }
                        i = endIndex + 2
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Strikethrough with ~~
                remaining.startsWith("~~") -> {
                    val endIndex = text.indexOf("~~", i + 2)
                    if (endIndex > i) {
                        val content = text.substring(i + 2, endIndex)
                        withStyle(SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = textColor.copy(alpha = 0.7f)
                        )) {
                            append(content)
                        }
                        i = endIndex + 2
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Inline code with `
                text[i] == '`' -> {
                    val endIndex = text.indexOf('`', i + 1)
                    if (endIndex > i) {
                        val content = text.substring(i + 1, endIndex)
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBgColor,
                            fontSize = 13.sp
                        )) {
                            append(" $content ")
                        }
                        i = endIndex + 1
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Italic with * (single asterisk, not followed by another)
                text[i] == '*' && !remaining.startsWith("**") -> {
                    // Find closing * that is not part of **
                    var endIndex = -1
                    var j = i + 1
                    while (j < text.length) {
                        if (text[j] == '*') {
                            // Check it's not followed by another *
                            if (j + 1 >= text.length || text[j + 1] != '*') {
                                // Check it's not preceded by another *
                                if (j - 1 >= 0 && text[j - 1] != '*') {
                                    endIndex = j
                                    break
                                } else if (j == i + 1) {
                                    // Empty italic, skip
                                    break
                                } else {
                                    endIndex = j
                                    break
                                }
                            }
                        }
                        j++
                    }
                    
                    if (endIndex > i + 1) {
                        val content = text.substring(i + 1, endIndex)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                            append(content)
                        }
                        i = endIndex + 1
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Italic with _ (single underscore)
                text[i] == '_' && !remaining.startsWith("__") -> {
                    var endIndex = -1
                    var j = i + 1
                    while (j < text.length) {
                        if (text[j] == '_' && (j + 1 >= text.length || text[j + 1] != '_')) {
                            endIndex = j
                            break
                        }
                        j++
                    }
                    
                    if (endIndex > i + 1) {
                        val content = text.substring(i + 1, endIndex)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                            append(content)
                        }
                        i = endIndex + 1
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Link [text](url)
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i)
                    if (closeBracket > i && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket) {
                            val linkText = text.substring(i + 1, closeBracket)
                            withStyle(SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )) {
                                append(linkText)
                            }
                            i = closeParen + 1
                        } else {
                            withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                            i++
                        }
                    } else {
                        withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                        i++
                    }
                }
                // Regular character
                else -> {
                    withStyle(SpanStyle(color = textColor)) { append(text[i]) }
                    i++
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("code", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

/**
 * Tool Call Block - Collapsible UI for MCP tool calls
 * Similar to ThinkBlock but for tool invocations
 */
@Composable
fun ToolCallBlock(
    content: String,
    isLoading: Boolean = false,
    isClosed: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(!isClosed) }
    var wasClosed by remember { mutableStateOf(isClosed) }
    
    // Parse tool/server details from content
    val toolName = remember(content) {
        Regex("""(?im)^Tool:\s*(.+)$""").find(content)?.groupValues?.get(1)
            ?: Regex(""""name"\s*:\s*"([^"]+)"""").find(content)?.groupValues?.get(1)
            ?: "Tool"
    }
    val serverName = remember(content) {
        Regex("""(?im)^MCP Server:\s*(.+)$""").find(content)?.groupValues?.get(1)
    }
    
    // Track isClosed transitions
    LaunchedEffect(isClosed) {
        if (isClosed && !wasClosed) {
            isExpanded = false
        } else if (!isClosed && wasClosed) {
            isExpanded = true
        }
        wasClosed = isClosed
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
    ) {
        // Header - clickable to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search/Tool icon
                Text(
                    text = "🔍",
                    fontSize = 16.sp
                )
                Text(
                    text = if (isLoading) {
                        "جاري استخدام الأداة..."
                    } else {
                        "تم استخدام MCP: ${serverName ?: toolName}"
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isLoading) {
                    LoadingDots()
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Expanded: show full content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = content,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
        
        // Collapsed: preview
        AnimatedVisibility(
            visible = !isExpanded && content.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = content.take(100) + if (content.length > 100) "..." else "",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                fontSize = 11.sp,
                maxLines = 2,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

/**
 * Search Results Block - Collapsible UI for search results
 * Shows results in a fold that starts collapsed by default
 */
@Composable
fun SearchResultsBlock(
    content: String,
    isLoading: Boolean = false,
    isClosed: Boolean = true
) {
    // Start collapsed by default when closed, expanded when streaming
    var isExpanded by remember { mutableStateOf(!isClosed) }
    var wasClosed by remember { mutableStateOf(isClosed) }
    
    // Auto-collapse when results are complete
    LaunchedEffect(isClosed) {
        if (isClosed && !wasClosed) {
            isExpanded = false
        } else if (!isClosed && wasClosed) {
            isExpanded = true
        }
        wasClosed = isClosed
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
    ) {
        // Header - clickable to toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Results icon
                Text(
                    text = "📋",
                    fontSize = 16.sp
                )
                Text(
                    text = if (isLoading) "جاري تحميل النتائج..." else "مخرجات الأداة",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (isLoading) {
                    LoadingDots()
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Expanded: show full content with scroll
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    text = content,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
        
        // Collapsed: short preview
        AnimatedVisibility(
            visible = !isExpanded && content.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = "اضغط لعرض ${content.lines().size} سطر من النتائج",
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}
