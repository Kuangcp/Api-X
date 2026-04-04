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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResponsePanel(
    modifier: Modifier = Modifier,
    statusCodeText: String,
    responseTimeText: String,
    responseSizeText: String,
    responseLines: List<String>,
    responsePartialLine: String?,
    responseHeaderLines: List<String>,
    rightTabIndex: Int,
    onRightTabIndexChange: (Int) -> Unit,
    isSseResponse: Boolean,
    /** 为 true 时不做 JSON 高亮（流式未完成或正加载）。 */
    isResponseLoading: Boolean = false,
    responseListState: LazyListState,
    responseHeadersListState: LazyListState
) {
    val isJsonContentType = remember(responseHeaderLines) {
        contentTypeHeaderIndicatesJson(responseHeaderLines)
    }
    val rawBodyCombined = remember(responseLines, responsePartialLine) {
        buildString {
            append(responseLines.joinToString("\n"))
            responsePartialLine?.let { p ->
                if (responseLines.isNotEmpty()) append('\n')
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RequestSideTopChromeTotalHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val darkTheme = !MaterialTheme.colors.isLight
                val metaColor = MaterialTheme.colors.onSurface
                Text(
                    text = statusCodeText,
                    color = when (statusCodeText.toIntOrNull()) {
                        null -> metaColor
                        200 -> if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                        else -> if (darkTheme) Color(0xFFFF8A80) else Color(0xFFC62828)
                    }
                )
                Text(" ", color = metaColor)
                Text("$responseTimeText ", color = metaColor)
                Text(responseSizeText, color = metaColor)
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
                    .height(24.dp)
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
                        fontSize = 12.sp,
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
                        fontSize = 12.sp,
                        color = if (rightTabIndex == 1) {
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
                when (rightTabIndex) {
                    0 -> {
                        if (jsonAnnotatedBody != null) {
                            SelectionContainer {
                                Text(
                                    text = jsonAnnotatedBody,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
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
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colors.onSurface,
                                        )
                                    }
                                    responsePartialLine?.let { partial ->
                                        item("partial") {
                                            Text(
                                                partial,
                                                fontSize = 12.sp,
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
                    else -> {
                        SelectionContainer {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 12.dp),
                                state = responseHeadersListState
                            ) {
                                items(responseHeaderLines) { line ->
                                    Text(
                                        line,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colors.onSurface
                                    )
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(responseHeadersListState)
                        )
                    }
                }
            }
        }
    }
}
