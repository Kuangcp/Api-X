package http.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import http.ExchangeFontMetrics

private val requestMethodDropdownChoices = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")

/**
 * 左侧整栏：方法/URL/发送 + Body/Headers 编辑区（与右侧响应区对称分栏；顶栏由 [RequestTopBar] 单独提供）。
 */
@Composable
fun RequestSidePanel(
    modifier: Modifier = Modifier,
    exchangeMetrics: ExchangeFontMetrics,
    isDarkTheme: Boolean,
    props: RequestEditorProps,
) {
    val p = props
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(exchangeMetrics.urlBarHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    val methodButtonColors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colors.onSurface,
                        disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                    )
                    TextButton(
                        onClick = { p.onMethodMenuExpandedChange(true) },
                        enabled = !p.isLoading,
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = methodButtonColors
                    ) {
                        Text(p.method, fontSize = exchangeMetrics.tab)
                    }
                    DropdownMenu(
                        expanded = p.methodMenuExpanded,
                        onDismissRequest = { p.onMethodMenuExpandedChange(false) }
                    ) {
                        for (m in requestMethodDropdownChoices) {
                            DropdownMenuItem(onClick = {
                                p.onMethodSelected(m)
                                p.onMethodMenuExpandedChange(false)
                            }) {
                                Text(m, fontSize = exchangeMetrics.tab, color = MaterialTheme.colors.onSurface)
                            }
                        }
                    }
                }
                UrlInputWithInlineSend(
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                    url = p.url,
                    onUrlChange = p.onUrlChange,
                    urlFieldEnabled = !p.isLoading,
                    isLoading = p.isLoading,
                    onSendOrCancel = p.onSendOrCancel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RequestEditorPane(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            exchangeMetrics = exchangeMetrics,
            editorRequestId = p.editorRequestId,
            isLoading = p.isLoading,
            isDarkTheme = isDarkTheme,
            leftTabIndex = p.leftTabIndex,
            onLeftTabIndexChange = p.onLeftTabIndexChange,
            bodyText = p.bodyText,
            onBodyTextChange = p.onBodyTextChange,
            headersText = p.headersText,
            onHeadersTextChange = p.onHeadersTextChange,
            paramsText = p.paramsText,
            onParamsTextChange = p.onParamsTextChange,
            auth = p.auth,
            onAuthChange = p.onAuthChange,
        )
    }
}

/**
 * URL 输入与发送/取消合成一块：外层圆角描边，输入区无边框，右侧小图标形似嵌在输入框内。
 */
@Composable
private fun UrlInputWithInlineSend(
    exchangeMetrics: ExchangeFontMetrics,
    isDarkTheme: Boolean,
    url: String,
    onUrlChange: (String) -> Unit,
    urlFieldEnabled: Boolean,
    isLoading: Boolean,
    onSendOrCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colors.onSurface.copy(
        alpha = if (urlFieldEnabled) ContentAlpha.medium else ContentAlpha.disabled
    )
    val cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black)
    Row(
        modifier = modifier
            .height(exchangeMetrics.urlBarHeight)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = url,
            onValueChange = onUrlChange,
            cursorBrush = cursorBrush,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .defaultMinSize(minHeight = 0.dp),
            enabled = urlFieldEnabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(
                fontSize = exchangeMetrics.body,
                color = MaterialTheme.colors.onSurface.copy(
                    alpha = if (urlFieldEnabled) 1f else ContentAlpha.disabled
                )
            ),
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (url.isEmpty()) {
                            Text(
                                "输入 URL",
                                fontSize = exchangeMetrics.body,
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .height(22.dp)
                .width(1.dp)
                .background(borderColor.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(34.dp)
                .clickable(onClick = onSendOrCancel),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "取消请求",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colors.primary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送请求",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colors.primary
                )
            }
        }
    }
}
