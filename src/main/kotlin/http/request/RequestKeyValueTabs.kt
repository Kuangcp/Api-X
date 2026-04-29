package http.request

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import http.ExchangeFontMetrics

@Composable
fun BoxScope.RequestHeadersEditorTab(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    headersScrollState: ScrollState,
) {
    HeadersKeyValueEditor(
        exchangeMetrics = exchangeMetrics,
        editorRequestId = editorRequestId,
        isLoading = isLoading,
        isDarkTheme = isDarkTheme,
        text = headersText,
        onTextChange = onHeadersTextChange,
        scrollState = headersScrollState,
        hintLine = "",
        modifier = Modifier.fillMaxSize(),
        defaultEditMode = KeyValueEditorMode.Form,
    )
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(headersScrollState),
    )
}

@Composable
fun BoxScope.RequestParamsEditorTab(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    paramsText: String,
    onParamsTextChange: (String) -> Unit,
    paramsScrollState: ScrollState,
) {
    PlainKeyValueEditor(
        exchangeMetrics = exchangeMetrics,
        editorRequestId = editorRequestId,
        isLoading = isLoading,
        isDarkTheme = isDarkTheme,
        text = paramsText,
        onTextChange = onParamsTextChange,
        scrollState = paramsScrollState,
        hintLine = "可与 URL 栏内已有 ?query 用 & 合并。",
        modifier = Modifier.fillMaxSize(),
        defaultEditMode = KeyValueEditorMode.Form,
    )
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(paramsScrollState),
    )
}
