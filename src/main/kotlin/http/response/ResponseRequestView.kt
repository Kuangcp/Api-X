package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

@Composable
internal fun ResponseRequestView(
    exchangeMetrics: ExchangeFontMetrics,
    requestPlainText: String,
) {
    val scrollState = rememberScrollState()
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = exchangeMetrics.body,
        color = MaterialTheme.colors.onSurface,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            Text(
                text = requestPlainText,
                style = style,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp),
            )
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState),
        )
    }
}
