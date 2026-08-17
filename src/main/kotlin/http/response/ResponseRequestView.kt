package http.response

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

@Composable
internal fun ResponseRequestView(
    exchangeMetrics: ExchangeFontMetrics,
    requestPlainText: String,
    requestMetaText: String? = null,
) {
    val scrollState = rememberScrollState()
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = exchangeMetrics.body,
        color = MaterialTheme.colors.onSurface,
    )
    val metaStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = exchangeMetrics.body,
        color = MaterialTheme.colors.primary,
        fontWeight = FontWeight.Bold,
    )
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp),
            ) {
                if (requestMetaText != null) {
                    Text(text = requestMetaText, style = metaStyle)
                    Spacer(Modifier.height(8.dp))
                }
                Text(text = requestPlainText, style = style)
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState),
        )
    }
}
