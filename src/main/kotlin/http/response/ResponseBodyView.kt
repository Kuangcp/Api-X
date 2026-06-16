package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

internal sealed class JsonHighlightState {
    data object Idle : JsonHighlightState()
    data object Computing : JsonHighlightState()
    data class Ready(val lines: List<androidx.compose.ui.text.AnnotatedString>, val rawBody: String) : JsonHighlightState()
}

@Composable
internal fun ResponseBodyView(
    exchangeMetrics: ExchangeFontMetrics,
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
    val isJsonReady = jsonHighlightState is JsonHighlightState.Ready
    val isJsonComputing = jsonHighlightState is JsonHighlightState.Computing

    Column(modifier = Modifier.fillMaxSize()) {
        // JSON highlight toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (jsonBodyTooLarge && jsonSyntaxHighlightEnabled) "JSON 高亮 (响应过大，已禁用)" else "JSON 高亮",
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            )
            Switch(
                checked = jsonSyntaxHighlightEnabled,
                onCheckedChange = onJsonSyntaxHighlightEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colors.primary,
                    checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                ),
            )
        }

        // Main body content
        if (isJsonReady) {
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
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                state = responseListState,
            ) {
                itemsIndexed(responseLines) { index, line ->
                    if (line.isBlank()) {
                        Spacer(Modifier.fillMaxWidth().height(8.dp))
                    } else {
                        val isDataLine = line.startsWith("data:")
                        val displayLine = if (isDataLine) "data:  ${line.removePrefix("data:").trimStart()}" else line
                        val isMatch = searchActive && searchQuery.isNotBlank() &&
                                line.contains(searchQuery, ignoreCase = true)
                        val isCurrentMatch = isMatch &&
                                matchingLineIndices.getOrNull(currentMatchIndex) == index
                        val isSelectedSseEvent = isSseResponse && isDataLine &&
                                index == selectedSseEventIndex
                        Text(
                            buildAnnotatedString {
                                if (isDataLine) {
                                    withStyle(SpanStyle(color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f))) {
                                        append("data:  ")
                                    }
                                    withStyle(SpanStyle(color = MaterialTheme.colors.onSurface)) {
                                        append(line.removePrefix("data:").trimStart())
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
                                    if (isSseResponse && isDataLine && !isResponseLoading) {
                                        Modifier.clickable {
                                            onSseEventClick(if (selectedSseEventIndex == index) -1 else index)
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
    }
}

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
