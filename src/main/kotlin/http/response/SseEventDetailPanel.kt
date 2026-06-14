package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics
import http.highlightJsonLinesOrNull

/**
 * 提取 SSE 事件中连续 data: 行的有效负载。
 */
internal fun extractSseEventData(lines: List<String>, clickedIndex: Int): String? {
    val clickedLine = lines.getOrNull(clickedIndex) ?: return null
    if (!clickedLine.startsWith("data:")) return null
    var start = clickedIndex
    while (start - 1 >= 0) {
        val prev = lines[start - 1]
        if (prev.isBlank()) break
        if (!prev.startsWith("data:")) break
        start--
    }
    val parts = mutableListOf<String>()
    var i = start
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) break
        if (!line.startsWith("data:")) break
        parts.add(line.removePrefix("data:").trimStart())
        i++
    }
    if (parts.isEmpty()) return null
    return parts.joinToString("\n")
}

@Composable
internal fun SseEventDetailPanel(
    exchangeMetrics: ExchangeFontMetrics,
    responseLines: List<String>,
    selectedSseEventIndex: Int,
    onDismiss: () -> Unit,
    sseEventPanelHeight: Float,
    onSseEventPanelHeightChange: (Float) -> Unit,
    sseContentHeightPx: Float,
    listState: LazyListState,
) {
    val darkTheme = !MaterialTheme.colors.isLight
    val ssePayload = remember(selectedSseEventIndex, responseLines.size) {
        extractSseEventData(responseLines, selectedSseEventIndex)
    }
    val sseHighlightedLines = remember(ssePayload, darkTheme) {
        ssePayload?.let { highlightJsonLinesOrNull(it, darkTheme) }
    }

    var dragHeight by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        dragHeight = sseEventPanelHeight
    }
    val panelHeightDp = with(LocalDensity.current) { dragHeight.toDp() }

    Column {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragHeight = sseEventPanelHeight },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val maxHeight = (sseContentHeightPx * 0.7f).coerceAtLeast(200f)
                            dragHeight = (dragHeight - dragAmount.y).coerceIn(80f, maxHeight)
                        },
                        onDragEnd = { onSseEventPanelHeightChange(dragHeight) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            )
        }

        // Detail content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeightDp)
                .background(MaterialTheme.colors.surface)
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Event #${selectedSseEventIndex + 1}",
                    fontSize = exchangeMetrics.tab,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                }
            }

            if (sseHighlightedLines != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                            state = listState,
                        ) {
                            itemsIndexed(sseHighlightedLines) { _, line ->
                                Text(
                                    text = line,
                                    style = TextStyle(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = exchangeMetrics.body,
                                        color = MaterialTheme.colors.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState),
                    )
                }
            } else if (ssePayload != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                            state = listState,
                        ) {
                            itemsIndexed(ssePayload.lines()) { _, line ->
                                Text(
                                    text = line,
                                    style = TextStyle(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = exchangeMetrics.body,
                                        color = MaterialTheme.colors.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState),
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "非 JSON 内容",
                        fontSize = exchangeMetrics.tab,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                }
            }
        }
    }
}
