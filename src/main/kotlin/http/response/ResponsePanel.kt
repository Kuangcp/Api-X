package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import db.HistoryEntry
import http.ExchangeFontMetrics
import http.HttpExchangeErrorStatusMark
import http.contentTypeHeaderIndicatesJson
import http.formatAndHighlightJsonOrNull
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.width
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** 响应头展示用：按第一个 `:` 拆成 name / value（value 内可含冒号）。 */
private fun splitResponseHeaderLine(line: String): Pair<String, String> {
    val idx = line.indexOf(':')
    if (idx < 0) return line.trim() to ""
    val name = line.substring(0, idx).trim()
    val value = line.substring(idx + 1).trimStart()
    return name to value
}

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
    /** 最近一次实际发出的请求（METHOD URL + 头 + 正文），纯文本。 */
    requestPlainText: String,
    rightTabIndex: Int,
    onRightTabIndexChange: (Int) -> Unit,
    isSseResponse: Boolean,
    /** 为 true 时不做 JSON 高亮（流式未完成或正加载）。 */
    isResponseLoading: Boolean = false,
    responseListState: LazyListState,
    responseHeadersListState: LazyListState,
    copyResponseBodyEnabled: Boolean,
    onCopyResponseBody: () -> Unit,
    clearResponseLogsEnabled: Boolean,
    onClearResponseLogs: () -> Unit,
    /** 为 false 时 Body 始终按纯文本展示（不做 JSON 语法高亮），默认 true。 */
    jsonSyntaxHighlightEnabled: Boolean = true,
    onJsonSyntaxHighlightEnabledChange: (Boolean) -> Unit = {},
    /** 可选的响应历史记录列表。 */
    historyEntries: List<HistoryEntry> = emptyList(),
    /** 当前选中的历史时间戳，null 表示最新。 */
    selectedHistoryEpochMs: Long? = null,
    /** 选中某个历史时的回调，参数为 epochMs，null 表示选择"最新"。 */
    onHistorySelected: (Long?) -> Unit = {},
) {
    // mutableStateListOf 引用不变，不能以列表引用作为 remember 键，否则头/正文更新后仍用旧缓存。
    val headersSnapshot = responseHeaderLines.toList()
    val isJsonContentType = remember(headersSnapshot) {
        contentTypeHeaderIndicatesJson(headersSnapshot)
    }
    val linesSnapshot = responseLines.toList()
    val rawBodyCombined = remember(linesSnapshot, responsePartialLine) {
        buildString {
            append(linesSnapshot.joinToString("\n"))
            responsePartialLine?.let { p ->
                if (linesSnapshot.isNotEmpty()) append('\n')
                append(p)
            }
        }
    }
    val darkTheme = !MaterialTheme.colors.isLight
    val jsonAnnotatedBody = remember(
        rawBodyCombined,
        isJsonContentType,
        isSseResponse,
        isResponseLoading,
        darkTheme,
        jsonSyntaxHighlightEnabled,
    ) {
        if (!jsonSyntaxHighlightEnabled || !isJsonContentType || isSseResponse || isResponseLoading) {
            null
        } else {
            formatAndHighlightJsonOrNull(rawBodyCombined, darkTheme)
        }
    }
    val jsonBodyScrollState = rememberScrollState()
    val requestScrollState = rememberScrollState()

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }

    val matchingLineIndices = remember(linesSnapshot, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else linesSnapshot.mapIndexedNotNull { index, line ->
            if (line.contains(searchQuery, ignoreCase = true)) index else null
        }
    }
    val totalMatches = matchingLineIndices.size

    fun navigateToMatch(direction: Int) {
        if (matchingLineIndices.isEmpty()) return
        val next = (currentMatchIndex + direction + matchingLineIndices.size) % matchingLineIndices.size
        currentMatchIndex = next
    }

    LaunchedEffect(responseLines.size, responsePartialLine, isSseResponse, rightTabIndex, jsonAnnotatedBody, searchActive) {
        if (!searchActive && isSseResponse && rightTabIndex == 0 && jsonAnnotatedBody == null) {
            val total = responseLines.size + if (responsePartialLine != null) 1 else 0
            if (total > 0) {
                responseListState.scrollToItem(total - 1)
            }
        }
    }

    LaunchedEffect(currentMatchIndex, searchQuery, searchActive) {
        if (searchActive && searchQuery.isNotBlank() && matchingLineIndices.isNotEmpty()) {
            if (currentMatchIndex < matchingLineIndices.size) {
                responseListState.scrollToItem(matchingLineIndices[currentMatchIndex])
            }
        }
    }

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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(exchangeMetrics.urlBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val darkTheme = !MaterialTheme.colors.isLight
                val metaColor = MaterialTheme.colors.onSurface
                val tab = exchangeMetrics.tab
                Text(
                    text = statusCodeText,
                    fontSize = tab,
                    color = when {
                        statusCodeText == HttpExchangeErrorStatusMark ->
                            if (darkTheme) Color(0xFFFF5252) else Color(0xFFD32F2F)
                        else -> when (statusCodeText.toIntOrNull()) {
                            null -> metaColor
                            200 -> if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                            else -> if (darkTheme) Color(0xFFFF8A80) else Color(0xFFC62828)
                        }
                    }
                )
                Text(" ", fontSize = tab, color = metaColor)
                Text("$responseTimeText ", fontSize = tab, color = metaColor)
                if (responseSseEventCount.isNotBlank()) {
                    Text("$responseSizeText $responseSseEventCount", fontSize = tab, color = metaColor)
                } else {
                    Text(responseSizeText, fontSize = tab, color = metaColor)
                }
            }
            val iconTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            val iconBtnMod = Modifier.size(32.dp)
            val iconMod = Modifier.size(17.dp)
            IconButton(
                onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
                modifier = iconBtnMod,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索响应正文",
                    modifier = iconMod,
                    tint = if (searchActive) MaterialTheme.colors.primary else iconTint,
                )
            }
            IconButton(
                onClick = onCopyResponseBody,
                enabled = copyResponseBodyEnabled,
                modifier = iconBtnMod,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制响应正文到剪贴板",
                    modifier = iconMod,
                    tint = iconTint,
                )
            }
            IconButton(
                onClick = onClearResponseLogs,
                enabled = clearResponseLogsEnabled,
                modifier = iconBtnMod,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除响应与压测日志文件",
                    modifier = iconMod,
                    tint = iconTint,
                )
            }
            var historyMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { historyMenuExpanded = true },
                    enabled = historyEntries.isNotEmpty(),
                    modifier = iconBtnMod,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "选择历史响应",
                        modifier = iconMod,
                        tint = if (historyEntries.isNotEmpty()) iconTint else iconTint.copy(alpha = 0.3f),
                    )
                }
                DropdownMenu(
                    expanded = historyMenuExpanded,
                    onDismissRequest = { historyMenuExpanded = false },
                ) {
                    val latestEntry = historyEntries.firstOrNull()
                    val isLatestSelected = selectedHistoryEpochMs == null ||
                        (latestEntry != null && selectedHistoryEpochMs == latestEntry.epochMs)
                    DropdownMenuItem(
                        onClick = {
                            onHistorySelected(null)
                            historyMenuExpanded = false
                        },
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text(
                            "最新",
                            fontSize = exchangeMetrics.tab,
                            color = if (isLatestSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                        )
                    }
                    if (historyEntries.isNotEmpty()) {
                        DropdownMenuItem(onClick = {}, enabled = false, modifier = Modifier.height(20.dp)) {
                            Divider()
                        }
                        for (entry in historyEntries) {
                            val isSelected = selectedHistoryEpochMs == entry.epochMs
                            DropdownMenuItem(
                                onClick = {
                                    onHistorySelected(entry.epochMs)
                                    historyMenuExpanded = false
                                },
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text(
                                    entry.displayTime,
                                    fontSize = exchangeMetrics.tab,
                                    color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(exchangeMetrics.editorTabStripHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (rightTabIndex == 0) {
                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onRightTabIndexChange(0) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Body",
                        fontSize = exchangeMetrics.tab,
                        color = if (rightTabIndex == 0) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (rightTabIndex == 1) {
                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onRightTabIndexChange(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Headers",
                        fontSize = exchangeMetrics.tab,
                        color = if (rightTabIndex == 1) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (rightTabIndex == 2) {
                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onRightTabIndexChange(2) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Request",
                        fontSize = exchangeMetrics.tab,
                        color = if (rightTabIndex == 2) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colors.surface)
                    .padding(12.dp)
            ) {
                when {
                    rightTabIndex == 2 -> {
                        val reqStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = exchangeMetrics.body,
                            color = MaterialTheme.colors.onSurface,
                        )
                        SelectionContainer {
                            Text(
                                text = requestPlainText,
                                style = reqStyle,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(requestScrollState)
                                    .padding(end = 12.dp),
                            )
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(requestScrollState),
                        )
                    }
                    rightTabIndex == 1 -> {
                        val headerStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = exchangeMetrics.body,
                            color = MaterialTheme.colors.onSurface,
                        )
                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 12.dp),
                                state = responseHeadersListState
                            ) {
                                items(
                                    count = responseHeaderLines.size,
                                    key = { it },
                                ) { index ->
                                    val (name, value) = splitResponseHeaderLine(responseHeaderLines[index])
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = name,
                                            modifier = Modifier.weight(1f),
                                            style = headerStyle,
                                            textAlign = TextAlign.Start,
                                        )
                                        Text(
                                            text = value,
                                            modifier = Modifier.weight(1f),
                                            style = headerStyle,
                                            textAlign = TextAlign.Start,
                                        )
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(responseHeadersListState)
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "JSON 高亮",
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
                            if (searchActive) {
                                LaunchedEffect(Unit) {
                                    delay(50)
                                    searchFocusRequester.requestFocus()
                                }
                                val searchTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                val searchTintDim = searchTint.copy(alpha = 0.3f)
                                val searchBg = MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
                                val searchBorder = MaterialTheme.colors.onSurface.copy(alpha = 0.2f)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(searchBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it; currentMatchIndex = 0 },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        placeholder = {
                                            Text(
                                                "搜索...",
                                                fontSize = exchangeMetrics.tab,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                            )
                                        },
                                        singleLine = true,
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            textColor = MaterialTheme.colors.onSurface,
                                            cursorColor = MaterialTheme.colors.primary,
                                            focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.6f),
                                            unfocusedBorderColor = searchBorder,
                                            backgroundColor = Color.Transparent,
                                        ),
                                    )
                                    Text(
                                        text = if (totalMatches > 0) "${currentMatchIndex + 1}/$totalMatches" else "0/0",
                                        fontSize = exchangeMetrics.tab,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                    IconButton(
                                        onClick = { navigateToMatch(-1) },
                                        modifier = Modifier.size(28.dp),
                                        enabled = totalMatches > 0,
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowUp,
                                            contentDescription = "上一个匹配",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (totalMatches > 0) searchTint else searchTintDim,
                                        )
                                    }
                                    IconButton(
                                        onClick = { navigateToMatch(1) },
                                        modifier = Modifier.size(28.dp),
                                        enabled = totalMatches > 0,
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "下一个匹配",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (totalMatches > 0) searchTint else searchTintDim,
                                        )
                                    }
                                    IconButton(
                                        onClick = { searchActive = false; searchQuery = "" },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "关闭搜索",
                                            modifier = Modifier.size(16.dp),
                                            tint = searchTint,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                if (jsonAnnotatedBody != null) {
                                    SelectionContainer {
                                        Text(
                                            text = jsonAnnotatedBody,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = exchangeMetrics.body,
                                                color = MaterialTheme.colors.onSurface,
                                            ),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(jsonBodyScrollState)
                                                .padding(end = 12.dp),
                                        )
                                    }
                                    VerticalScrollbar(
                                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                        adapter = rememberScrollbarAdapter(jsonBodyScrollState),
                                    )
                                } else {
                                    SelectionContainer {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(end = 12.dp),
                                            state = responseListState,
                                        ) {
                                            itemsIndexed(responseLines) { index, line ->
                                                val isMatch = searchActive && searchQuery.isNotBlank() &&
                                                    line.contains(searchQuery, ignoreCase = true)
                                                val isCurrentMatch = isMatch &&
                                                    matchingLineIndices.getOrNull(currentMatchIndex) == index
                                                Text(
                                                    line,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .then(
                                                            if (isCurrentMatch) {
                                                                Modifier.background(
                                                                    MaterialTheme.colors.primary.copy(alpha = 0.25f)
                                                                )
                                                            } else if (isMatch) {
                                                                Modifier.background(
                                                                    MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                                                                )
                                                            } else Modifier
                                                        ),
                                                    fontSize = exchangeMetrics.body,
                                                    color = MaterialTheme.colors.onSurface,
                                                )
                                            }
                                            responsePartialLine?.let { partial ->
                                                item("partial") {
                                                    Text(
                                                        partial,
                                                        fontSize = exchangeMetrics.body,
                                                        color = MaterialTheme.colors.onSurface,
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
                        }
                    }
                }
            }
        }
    }
}
