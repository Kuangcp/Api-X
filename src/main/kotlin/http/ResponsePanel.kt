package http

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    ) {
        if (!isJsonContentType || isSseResponse || isResponseLoading) {
            null
        } else {
            formatAndHighlightJsonOrNull(rawBodyCombined, darkTheme)
        }
    }
    val jsonBodyScrollState = rememberScrollState()
    val requestScrollState = rememberScrollState()

    LaunchedEffect(responseLines.size, responsePartialLine, isSseResponse, rightTabIndex, jsonAnnotatedBody) {
        if (isSseResponse && rightTabIndex == 0 && jsonAnnotatedBody == null) {
            val total = responseLines.size + if (responsePartialLine != null) 1 else 0
            if (total > 0) {
                responseListState.scrollToItem(total - 1)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxHeight().fillMaxWidth().padding(start = 6.dp),
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
                Text(responseSizeText, fontSize = tab, color = metaColor)
            }
            val iconTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            val iconBtnMod = Modifier.size(32.dp)
            val iconMod = Modifier.size(17.dp)
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
                                    items(responseLines) { line ->
                                        Text(
                                            line,
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
