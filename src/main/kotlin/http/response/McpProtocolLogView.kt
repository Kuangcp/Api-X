package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class McpProtocolMode {
    Messages,
    Notifications,
}

private enum class McpEntryKind {
    Message,
    Notification,
}

private data class McpProtocolEntry(
    val kind: McpEntryKind,
    val direction: String,
    val title: String,
    val rawJson: String,
)

private val mcpProtocolJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

@Composable
internal fun McpProtocolLogView(
    exchangeMetrics: ExchangeFontMetrics,
    mode: McpProtocolMode,
    responseLines: List<String>,
    responsePartialLine: String?,
    listState: LazyListState,
) {
    val entries = remember(responseLines, responsePartialLine) {
        parseMcpProtocolEntries(responseLines, responsePartialLine)
    }
    val filtered = remember(entries, mode) {
        entries.filter {
            when (mode) {
                McpProtocolMode.Messages -> it.kind == McpEntryKind.Message
                McpProtocolMode.Notifications -> it.kind == McpEntryKind.Notification
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (mode == McpProtocolMode.Messages) "No MCP messages parsed yet" else "No MCP notifications parsed yet",
                    fontSize = exchangeMetrics.tab,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                )
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    state = listState,
                ) {
                    items(filtered) { entry ->
                        McpProtocolEntryCard(exchangeMetrics, entry)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState),
            )
        }
    }
}

@Composable
private fun McpProtocolEntryCard(
    exchangeMetrics: ExchangeFontMetrics,
    entry: McpProtocolEntry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
            .padding(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.direction,
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = entry.title,
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.rawJson,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = exchangeMetrics.body,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.88f),
            ),
        )
    }
}

private fun parseMcpProtocolEntries(
    lines: List<String>,
    partialLine: String?,
): List<McpProtocolEntry> {
    val entries = mutableListOf<McpProtocolEntry>()
    val allLines = if (partialLine.isNullOrBlank()) lines else lines + partialLine
    var direction = "resp"
    val buffer = StringBuilder()

    fun flushBuffer() {
        if (buffer.isBlank()) return
        parseEntry(buffer.toString(), direction)?.let(entries::add)
        buffer.clear()
    }

    for (line in allLines) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith(">>>") -> {
                flushBuffer()
                direction = "req"
            }
            trimmed.startsWith("<<<") -> {
                flushBuffer()
                direction = "resp"
                val inlineJson = trimmed.removePrefix("<<<").trim()
                if (inlineJson.startsWith("{")) {
                    buffer.appendLine(inlineJson)
                    flushBuffer()
                }
            }
            trimmed.startsWith("{") || buffer.isNotBlank() -> {
                buffer.appendLine(trimmed)
                if (runCatching { mcpProtocolJson.parseToJsonElement(buffer.toString()) }.isSuccess) {
                    flushBuffer()
                }
            }
        }
    }
    flushBuffer()
    return entries
}

private fun parseEntry(raw: String, direction: String): McpProtocolEntry? {
    val obj = runCatching { mcpProtocolJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val method = obj["method"]?.jsonPrimitive?.contentOrNull
    val id = obj["id"]?.jsonPrimitive?.contentOrNull
    val kind = if (method != null && id == null) McpEntryKind.Notification else McpEntryKind.Message
    val title = when {
        method != null && id != null -> "$method #$id"
        method != null -> method
        obj["error"] != null && id != null -> "error #$id"
        obj["result"] != null && id != null -> "result #$id"
        obj["error"] != null -> "error"
        obj["result"] != null -> "result"
        else -> "json-rpc"
    }
    return McpProtocolEntry(
        kind = kind,
        direction = direction,
        title = title,
        rawJson = prettyJson(obj),
    )
}

private fun prettyJson(obj: JsonObject): String =
    runCatching { mcpProtocolJson.encodeToString(JsonElement.serializer(), obj) }.getOrElse { obj.toString() }
