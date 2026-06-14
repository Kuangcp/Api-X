package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

/** 响应头展示用：按第一个 `:` 拆成 name / value（value 内可含冒号）。 */
internal fun splitResponseHeaderLine(line: String): Pair<String, String> {
    val idx = line.indexOf(':')
    if (idx < 0) return line.trim() to ""
    val name = line.substring(0, idx).trim()
    val value = line.substring(idx + 1).trimStart()
    return name to value
}

@Composable
internal fun ResponseHeadersView(
    exchangeMetrics: ExchangeFontMetrics,
    responseHeaderLines: List<String>,
    listState: LazyListState,
) {
    val headerStyle = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = exchangeMetrics.body,
        color = MaterialTheme.colors.onSurface,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                state = listState,
            ) {
                items(count = responseHeaderLines.size, key = { it }) { index ->
                    val (name, value) = splitResponseHeaderLine(responseHeaderLines[index])
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
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
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}
