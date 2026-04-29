package http.request

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics
import tree.PostmanAuth

/** 左侧 Body / Headers / Params 编辑区（分割条左侧） */
@Composable
fun RequestEditorPane(
    modifier: Modifier = Modifier,
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    leftTabIndex: Int,
    onLeftTabIndexChange: (Int) -> Unit,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    paramsText: String,
    onParamsTextChange: (String) -> Unit,
    auth: PostmanAuth?,
    onAuthChange: (PostmanAuth?) -> Unit,
) {
    val bodyScrollState = rememberScrollState()
    val headersScrollState = rememberScrollState()
    val paramsScrollState = rememberScrollState()
    val authScrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(exchangeMetrics.editorTabStripHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
        ) {
            val tabs = listOf("Body", "Headers", "Params", "Auth")
            tabs.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (leftTabIndex == index) {
                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable(enabled = !isLoading) { onLeftTabIndexChange(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = exchangeMetrics.tab,
                        color = if (leftTabIndex == index) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            when (leftTabIndex) {
                0 -> {
                    RequestBodyEditorTab(
                        exchangeMetrics = exchangeMetrics,
                        editorRequestId = editorRequestId,
                        isLoading = isLoading,
                        isDarkTheme = isDarkTheme,
                        bodyText = bodyText,
                        onBodyTextChange = onBodyTextChange,
                        headersText = headersText,
                        onHeadersTextChange = onHeadersTextChange,
                        bodyScrollState = bodyScrollState,
                    )
                }
                1 -> {
                    RequestHeadersEditorTab(
                        exchangeMetrics = exchangeMetrics,
                        editorRequestId = editorRequestId,
                        isLoading = isLoading,
                        isDarkTheme = isDarkTheme,
                        headersText = headersText,
                        onHeadersTextChange = onHeadersTextChange,
                        headersScrollState = headersScrollState,
                    )
                }
                2 -> {
                    RequestParamsEditorTab(
                        exchangeMetrics = exchangeMetrics,
                        editorRequestId = editorRequestId,
                        isLoading = isLoading,
                        isDarkTheme = isDarkTheme,
                        paramsText = paramsText,
                        onParamsTextChange = onParamsTextChange,
                        paramsScrollState = paramsScrollState,
                    )
                }
                3 -> {
                    AuthEditor(
                        auth = auth,
                        onAuthChange = onAuthChange,
                        exchangeMetrics = exchangeMetrics,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.fillMaxSize().padding(end = 10.dp).verticalScroll(authScrollState)
                    )
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(authScrollState),
                    )
                }
            }
        }
    }
}
