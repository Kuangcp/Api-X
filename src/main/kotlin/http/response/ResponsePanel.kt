package http.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import db.HistoryEntry
import http.ExchangeFontMetrics
import http.contentTypeHeaderIndicatesJson
import http.highlightJsonLinesOrNull
import kotlinx.coroutines.launch

@Composable
fun ResponsePanel(
    modifier: Modifier = Modifier,
    exchangeMetrics: ExchangeFontMetrics,
    statusCodeText: String,
    responseTimeText: String,
    responseSizeText: String,
    responseSseEventCount: String = "",
    responseLines: List<String>,
    responsePartialLine: String?,
    responseHeaderLines: List<String>,
    requestPlainText: String,
    rightTabIndex: Int,
    onRightTabIndexChange: (Int) -> Unit,
    isSseResponse: Boolean,
    isResponseLoading: Boolean = false,
    isCacheLoading: Boolean = false,
    responseListState: LazyListState,
    responseHeadersListState: LazyListState,
    copyResponseBodyEnabled: Boolean,
    onCopyResponseBody: () -> Unit,
    clearResponseLogsEnabled: Boolean,
    onClearResponseLogs: () -> Unit,
    jsonSyntaxHighlightEnabled: Boolean = true,
    onJsonSyntaxHighlightEnabledChange: (Boolean) -> Unit = {},
    historyEntries: List<HistoryEntry> = emptyList(),
    selectedHistoryEpochMs: Long? = null,
    onHistorySelected: (Long?) -> Unit = {},
) {
    // ── State ────────────────────────────────────────────────
    val headersSnapshot = responseHeaderLines.toList()
    val isJsonContentType = remember(headersSnapshot) { contentTypeHeaderIndicatesJson(headersSnapshot) }
    val linesSize = responseLines.size
    val rawBodyCombined = remember(linesSize, responsePartialLine) {
        buildString {
            if (responseLines.isNotEmpty()) {
                responseLines.forEachIndexed { i, line ->
                    if (i > 0) append('\n')
                    append(line)
                }
            }
            responsePartialLine?.let { p ->
                if (responseLines.isNotEmpty()) append('\n')
                append(p)
            }
        }
    }
    val darkTheme = !MaterialTheme.colors.isLight
    var jsonHighlightState by remember { mutableStateOf<JsonHighlightState>(JsonHighlightState.Idle) }
    val shouldHighlight = jsonSyntaxHighlightEnabled && isJsonContentType && !isSseResponse && !isResponseLoading
    LaunchedEffect(rawBodyCombined, shouldHighlight, darkTheme) {
        if (!shouldHighlight) {
            jsonHighlightState = JsonHighlightState.Idle
            return@LaunchedEffect
        }
        jsonHighlightState = JsonHighlightState.Computing
        val result = highlightJsonLinesOrNull(rawBodyCombined, darkTheme)
        jsonHighlightState = if (result != null) JsonHighlightState.Ready(result, rawBodyCombined) else JsonHighlightState.Idle
    }

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }

    var selectedSseEventIndex by remember { mutableIntStateOf(-1) }
    var sseEventPanelHeight by remember { mutableStateOf(0f) }
    var sseContentHeightPx by remember { mutableIntStateOf(0) }
    val sseDetailListState = remember { LazyListState() }
    val scope = rememberCoroutineScope()

    val matchingLineIndices = remember(responseLines.size, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else responseLines.mapIndexedNotNull { index, line ->
            if (line.contains(searchQuery, ignoreCase = true)) index else null
        }
    }
    val totalMatches = matchingLineIndices.size

    fun navigateToMatch(direction: Int) {
        if (matchingLineIndices.isEmpty()) return
        val next = (currentMatchIndex + direction + matchingLineIndices.size) % matchingLineIndices.size
        currentMatchIndex = next
    }

    // ── Auto-scroll effects ─────────────────────────────────
    LaunchedEffect(responseLines.size, responsePartialLine, isSseResponse, rightTabIndex, jsonHighlightState, searchActive) {
        if (!searchActive && isSseResponse && rightTabIndex == 0 && jsonHighlightState !is JsonHighlightState.Ready) {
            val total = responseLines.size + if (responsePartialLine != null) 1 else 0
            if (total > 0) responseListState.scrollToItem(total - 1)
        }
    }
    LaunchedEffect(currentMatchIndex, searchQuery, searchActive) {
        if (searchActive && searchQuery.isNotBlank() && matchingLineIndices.isNotEmpty()) {
            if (currentMatchIndex < matchingLineIndices.size) {
                responseListState.scrollToItem(matchingLineIndices[currentMatchIndex])
            }
        }
    }
    LaunchedEffect(responseLines.size) {
        selectedSseEventIndex = -1
        sseEventPanelHeight = 0f
    }
    LaunchedEffect(isResponseLoading) {
        if (isResponseLoading) {
            selectedSseEventIndex = -1
            sseEventPanelHeight = 0f
        }
    }
    LaunchedEffect(selectedSseEventIndex) {
        if (selectedSseEventIndex >= 0) sseDetailListState.scrollToItem(0)
    }

    // ── UI ──────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(start = 6.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.isCtrlPressed || event.isMetaPressed) &&
                    event.key == Key.F
                ) {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                    true
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Toolbar: status code, metrics, action buttons
        ResponseToolbar(
            exchangeMetrics = exchangeMetrics,
            statusCodeText = statusCodeText,
            responseTimeText = responseTimeText,
            responseSizeText = responseSizeText,
            responseSseEventCount = responseSseEventCount,
            searchActive = searchActive,
            onToggleSearch = { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
            copyResponseBodyEnabled = copyResponseBodyEnabled,
            onCopyResponseBody = onCopyResponseBody,
            clearResponseLogsEnabled = clearResponseLogsEnabled,
            onClearResponseLogs = onClearResponseLogs,
            historyEntries = historyEntries,
            selectedHistoryEpochMs = selectedHistoryEpochMs,
            onHistorySelected = onHistorySelected,
        )

        // Tab strip + content area
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ResponseTabStrip(
                exchangeMetrics = exchangeMetrics,
                rightTabIndex = rightTabIndex,
                onRightTabIndexChange = onRightTabIndexChange,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colors.surface)
                    .padding(12.dp),
            ) {
                when (rightTabIndex) {
                    2 -> ResponseRequestView(exchangeMetrics, requestPlainText)
                    1 -> ResponseHeadersView(exchangeMetrics, responseHeaderLines, responseHeadersListState)
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar
                        if (searchActive) {
                            ResponseSearchBar(
                                exchangeMetrics = exchangeMetrics,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it; currentMatchIndex = 0 },
                                currentMatchIndex = currentMatchIndex,
                                totalMatches = totalMatches,
                                onNavigateMatch = { navigateToMatch(it) },
                                onClose = { searchActive = false; searchQuery = "" },
                                focusRequester = searchFocusRequester,
                            )
                        }
                        // Body content
                        ResponseBodyView(
                            exchangeMetrics = exchangeMetrics,
                            responseLines = responseLines,
                            responsePartialLine = responsePartialLine,
                            responseListState = responseListState,
                            jsonHighlightState = jsonHighlightState,
                            jsonSyntaxHighlightEnabled = jsonSyntaxHighlightEnabled,
                            onJsonSyntaxHighlightEnabledChange = onJsonSyntaxHighlightEnabledChange,
                            isSseResponse = isSseResponse,
                            isResponseLoading = isResponseLoading,
                            isCacheLoading = isCacheLoading,
                            searchActive = searchActive,
                            searchQuery = searchQuery,
                            matchingLineIndices = matchingLineIndices,
                            currentMatchIndex = currentMatchIndex,
                            selectedSseEventIndex = selectedSseEventIndex,
                            onSseEventClick = { index ->
                                selectedSseEventIndex = index
                                if (index >= 0 && sseEventPanelHeight < 1f) sseEventPanelHeight = 200f
                            },
                            sseContentHeightPx = sseContentHeightPx,
                            onSseContentHeightChange = { sseContentHeightPx = it },
                            sseEventPanelHeight = sseEventPanelHeight,
                            onSseEventPanelHeightChange = { sseEventPanelHeight = it },
                            sseDetailListState = sseDetailListState,
                        )
                    }
                }

                // Scroll-to-top button
                IconButton(
                    onClick = {
                        when (rightTabIndex) {
                            0 -> scope.launch { responseListState.animateScrollToItem(0) }
                            1 -> scope.launch { responseHeadersListState.animateScrollToItem(0) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.85f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "回到顶部",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.onPrimary,
                    )
                }
            }
        }
    }
}
