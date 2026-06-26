package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Popup
import app.core.writeClipboardText
import http.ExchangeFontMetrics

internal enum class ResponseBodyRenderMode {
    Raw,
    Model,
}

private enum class BodyViewMode {
    Raw,
    Messages,
    Notifications,
}

internal sealed class JsonHighlightState {
    data object Idle : JsonHighlightState()
    data object Computing : JsonHighlightState()
    data class Ready(val lines: List<androidx.compose.ui.text.AnnotatedString>, val rawBody: String) : JsonHighlightState()
}

@Composable
internal fun ResponseBodyView(
    exchangeMetrics: ExchangeFontMetrics,
    renderMode: ResponseBodyRenderMode,
    onRenderModeChange: (ResponseBodyRenderMode) -> Unit,
    modelOutputAvailable: Boolean,
    modelOutputChunkCount: Int,
    mcpProtocolViewAvailable: Boolean,
    responseLines: List<String>,
    responsePartialLine: String?,
    responseListState: LazyListState,
    jsonHighlightState: JsonHighlightState,
    jsonSyntaxHighlightEnabled: Boolean,
    onJsonSyntaxHighlightEnabledChange: (Boolean) -> Unit,
    jsonBodyTooLarge: Boolean,
    isSseResponse: Boolean,
    isResponseLoading: Boolean,
    isCacheLoading: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    matchingLineIndices: List<Int>,
    currentMatchIndex: Int,
    selectedSseEventIndex: Int,
    onSseEventClick: (Int) -> Unit,
    sseContentHeightPx: Int,
    onSseContentHeightChange: (Int) -> Unit,
    sseEventPanelHeight: Float,
    onSseEventPanelHeightChange: (Float) -> Unit,
    sseDetailListState: LazyListState,
) {
    var bodyViewMode by remember { mutableStateOf(BodyViewMode.Raw) }
    val isJsonReady = bodyViewMode == BodyViewMode.Raw && renderMode == ResponseBodyRenderMode.Raw && jsonHighlightState is JsonHighlightState.Ready
    val isJsonComputing = bodyViewMode == BodyViewMode.Raw && renderMode == ResponseBodyRenderMode.Raw && jsonHighlightState is JsonHighlightState.Computing
    val jsonToggleEnabled = bodyViewMode == BodyViewMode.Raw && renderMode == ResponseBodyRenderMode.Raw

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (mcpProtocolViewAvailable || modelOutputAvailable) {
                Row(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                ) {
                    ResponseBodyModeButton(
                        label = "Raw",
                        selected = bodyViewMode == BodyViewMode.Raw && renderMode == ResponseBodyRenderMode.Raw,
                        onClick = { bodyViewMode = BodyViewMode.Raw; onRenderModeChange(ResponseBodyRenderMode.Raw) },
                        exchangeMetrics = exchangeMetrics,
                    )
                    if (mcpProtocolViewAvailable) {
                        ResponseBodyModeButton(
                            label = "Messages",
                            selected = bodyViewMode == BodyViewMode.Messages,
                            onClick = { bodyViewMode = BodyViewMode.Messages },
                            exchangeMetrics = exchangeMetrics,
                        )
                        ResponseBodyModeButton(
                            label = "Notifications",
                            selected = bodyViewMode == BodyViewMode.Notifications,
                            onClick = { bodyViewMode = BodyViewMode.Notifications },
                            exchangeMetrics = exchangeMetrics,
                        )
                    }
                    if (modelOutputAvailable) {
                        ResponseBodyModeButton(
                            label = if (modelOutputChunkCount > 0) "Model ($modelOutputChunkCount)" else "Model",
                            selected = bodyViewMode == BodyViewMode.Raw && renderMode == ResponseBodyRenderMode.Model,
                            onClick = { bodyViewMode = BodyViewMode.Raw; onRenderModeChange(ResponseBodyRenderMode.Model) },
                            exchangeMetrics = exchangeMetrics,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (jsonBodyTooLarge && jsonSyntaxHighlightEnabled) "JSON highlight disabled" else "JSON highlight",
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            )
            Switch(
                checked = jsonSyntaxHighlightEnabled,
                onCheckedChange = onJsonSyntaxHighlightEnabledChange,
                enabled = jsonToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colors.primary,
                    checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                    disabledCheckedThumbColor = MaterialTheme.colors.onSurface.copy(alpha = 0.25f),
                    disabledCheckedTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                ),
            )
        }

        // Main body content
        if (bodyViewMode == BodyViewMode.Messages || bodyViewMode == BodyViewMode.Notifications) {
            McpProtocolLogView(
                exchangeMetrics = exchangeMetrics,
                mode = if (bodyViewMode == BodyViewMode.Messages) McpProtocolMode.Messages else McpProtocolMode.Notifications,
                responseLines = responseLines,
                responsePartialLine = responsePartialLine,
                listState = responseListState,
            )
        } else if (isJsonReady) {
            JsonReadyBody(exchangeMetrics, jsonHighlightState, responseListState)
        } else if (isCacheLoading) {
            CacheLoadingIndicator(exchangeMetrics)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PlainTextOrSseBody(
                    exchangeMetrics = exchangeMetrics,
                    responseLines = responseLines,
                    responsePartialLine = responsePartialLine,
                    responseListState = responseListState,
                    isSseResponse = isSseResponse,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    matchingLineIndices = matchingLineIndices,
                    currentMatchIndex = currentMatchIndex,
                    selectedSseEventIndex = selectedSseEventIndex,
                    onSseEventClick = onSseEventClick,
                    sseContentHeightPx = sseContentHeightPx,
                    onSseContentHeightChange = onSseContentHeightChange,
                    sseEventPanelHeight = sseEventPanelHeight,
                    onSseEventPanelHeightChange = onSseEventPanelHeightChange,
                    sseDetailListState = sseDetailListState,
                    isResponseLoading = isResponseLoading,
                )
                if (isJsonComputing) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colors.primary,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "解析中…",
                            fontSize = exchangeMetrics.tiny,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseBodyModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    exchangeMetrics: ExchangeFontMetrics,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = exchangeMetrics.tab,
            color = if (selected) MaterialTheme.colors.onSurface
            else MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
        )
    }
}

@Composable
private fun JsonReadyBody(
    exchangeMetrics: ExchangeFontMetrics,
    readyState: JsonHighlightState.Ready,
    listState: LazyListState,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                state = listState,
            ) {
                itemsIndexed(readyState.lines) { _, line ->
                    Text(
                        text = line,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
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
}

@Composable
private fun JsonComputingIndicator(exchangeMetrics: ExchangeFontMetrics) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "正在解析 JSON…",
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            )
        }
    }
}

@Composable
private fun CacheLoadingIndicator(exchangeMetrics: ExchangeFontMetrics) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colors.primary,
        )
    }
}

@Composable
private fun PlainTextOrSseBody(
    exchangeMetrics: ExchangeFontMetrics,
    responseLines: List<String>,
    responsePartialLine: String?,
    responseListState: LazyListState,
    isSseResponse: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    matchingLineIndices: List<Int>,
    currentMatchIndex: Int,
    selectedSseEventIndex: Int,
    onSseEventClick: (Int) -> Unit,
    sseContentHeightPx: Int,
    onSseContentHeightChange: (Int) -> Unit,
    sseEventPanelHeight: Float,
    onSseEventPanelHeightChange: (Float) -> Unit,
    sseDetailListState: LazyListState,
    isResponseLoading: Boolean,
) {
    val darkTheme = !MaterialTheme.colors.isLight

    Column(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { onSseContentHeightChange(it.size.height) },
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (isSseResponse || searchActive) {
                SseOrSearchBody(
                    exchangeMetrics = exchangeMetrics,
                    responseLines = responseLines,
                    responsePartialLine = responsePartialLine,
                    responseListState = responseListState,
                    isSseResponse = isSseResponse,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    matchingLineIndices = matchingLineIndices,
                    currentMatchIndex = currentMatchIndex,
                    selectedSseEventIndex = selectedSseEventIndex,
                    onSseEventClick = onSseEventClick,
                    sseEventPanelHeight = sseEventPanelHeight,
                    onSseEventPanelHeightChange = onSseEventPanelHeightChange,
                    isResponseLoading = isResponseLoading,
                )
            } else {
                PlainTextBody(exchangeMetrics, responseLines, responseListState)
            }
        }

        // SSE event detail panel
        if (isSseResponse && selectedSseEventIndex >= 0) {
            SseEventDetailPanel(
                exchangeMetrics = exchangeMetrics,
                responseLines = responseLines,
                selectedSseEventIndex = selectedSseEventIndex,
                onDismiss = { onSseEventClick(-1) },
                sseEventPanelHeight = sseEventPanelHeight,
                onSseEventPanelHeightChange = onSseEventPanelHeightChange,
                sseContentHeightPx = sseContentHeightPx.toFloat(),
                listState = sseDetailListState,
            )
        }
    }
}

@Composable
private fun SseOrSearchBody(
    exchangeMetrics: ExchangeFontMetrics,
    responseLines: List<String>,
    responsePartialLine: String?,
    responseListState: LazyListState,
    isSseResponse: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    matchingLineIndices: List<Int>,
    currentMatchIndex: Int,
    selectedSseEventIndex: Int,
    onSseEventClick: (Int) -> Unit,
    sseEventPanelHeight: Float,
    onSseEventPanelHeightChange: (Float) -> Unit,
    isResponseLoading: Boolean,
) {
    val boxCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val lineCoordinatesMap = remember { mutableMapOf<Int, LayoutCoordinates>() }
    var contextMenuTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxCoordinates.value = it }
    ) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                state = responseListState,
            ) {
                itemsIndexed(responseLines) { index, line ->
                    if (line.isBlank()) {
                        Spacer(Modifier.fillMaxWidth().height(8.dp))
                    } else {
                        val timestampSep = " data:"
                        val isDataLine = line.startsWith("data:") || (isSseResponse && line.contains(timestampSep))
                        val (timestampPart, sseDataContent) = if (line.startsWith("data:")) {
                            null to line.removePrefix("data:").trimStart()
                        } else {
                            val idx = line.indexOf(timestampSep)
                            if (idx >= 0) {
                                line.substring(0, idx) to line.substring(idx + timestampSep.length).trimStart()
                            } else {
                                null to ""
                            }
                        }
                        val displayLine = if (isDataLine) "data:  $sseDataContent" else line
                        val isMatch = searchActive && searchQuery.isNotBlank() &&
                                line.contains(searchQuery, ignoreCase = true)
                        val isCurrentMatch = isMatch &&
                                matchingLineIndices.getOrNull(currentMatchIndex) == index
                        val isSelectedSseEvent = isSseResponse && isDataLine &&
                                index == selectedSseEventIndex
                        val clickable = isSseResponse && isDataLine && !isResponseLoading
                        Text(
                            buildAnnotatedString {
                                if (isDataLine) {
                                    if (timestampPart != null) {
                                        withStyle(SpanStyle(color = MaterialTheme.colors.onSurface.copy(alpha = 0.25f))) {
                                            append("$timestampPart ")
                                        }
                                    }
                                    withStyle(SpanStyle(color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f))) {
                                        append("data:  ")
                                    }
                                    withStyle(SpanStyle(color = MaterialTheme.colors.onSurface)) {
                                        append(sseDataContent)
                                    }
                                } else {
                                    append(displayLine)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isSelectedSseEvent -> MaterialTheme.colors.primary.copy(alpha = 0.18f)
                                        isCurrentMatch -> MaterialTheme.colors.primary.copy(alpha = 0.25f)
                                        isMatch -> MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                        else -> MaterialTheme.colors.surface
                                    }
                                )
                                .then(
                                    if (clickable) {
                                        Modifier.clickable {
                                            onSseEventClick(if (selectedSseEventIndex == index) -1 else index)
                                        }
                                    } else Modifier
                                )
                                .then(
                                    if (isDataLine) {
                                        Modifier
                                            .onGloballyPositioned { lineCoordinatesMap[index] = it }
                                            .pointerInput(index) {
                                                @OptIn(ExperimentalComposeUiApi::class)
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        if (event.type == PointerEventType.Press && event.button == PointerButton.Secondary) {
                                                            val localPos = event.changes.first().position
                                                            val lineCoords = lineCoordinatesMap[index]
                                                            val boxCoords = boxCoordinates.value
                                                            if (lineCoords != null && boxCoords != null) {
                                                                val posInBox = boxCoords.localPositionOf(lineCoords, localPos)
                                                                contextMenuTarget = ContextMenuTarget(
                                                                    boxRelativeX = posInBox.x,
                                                                    boxRelativeY = posInBox.y,
                                                                    content = sseDataContent,
                                                                )
                                                            }
                                                            event.changes.first().consume()
                                                        }
                                                    }
                                                }
                                            }
                                    } else Modifier
                                ),
                            style = TextStyle(
                                fontSize = exchangeMetrics.body,
                                lineHeight = exchangeMetrics.body * 1.35f,
                                color = MaterialTheme.colors.onSurface,
                            ),
                        )
                    }
                }
                responsePartialLine?.let { partial ->
                    item("partial") {
                        Text(
                            partial,
                            style = TextStyle(
                                fontSize = exchangeMetrics.body,
                                lineHeight = exchangeMetrics.body * 1.35f,
                                color = MaterialTheme.colors.onSurface,
                            ),
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(responseListState),
        )

        contextMenuTarget?.let { target ->
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(target.boxRelativeX.toInt(), target.boxRelativeY.toInt()),
                onDismissRequest = { contextMenuTarget = null },
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colors.surface,
                    elevation = 8.dp,
                    modifier = Modifier.clickable {
                        writeClipboardText(target.content)
                        contextMenuTarget = null
                    },
                ) {
                    Text(
                        text = "Copy",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = exchangeMetrics.body,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
    }
}

private data class ContextMenuTarget(
    val boxRelativeX: Float,
    val boxRelativeY: Float,
    val content: String,
)

@Composable
private fun PlainTextBody(
    exchangeMetrics: ExchangeFontMetrics,
    responseLines: List<String>,
    listState: LazyListState,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                state = listState,
            ) {
                itemsIndexed(responseLines) { _, line ->
                    Text(
                        text = line,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
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
}
