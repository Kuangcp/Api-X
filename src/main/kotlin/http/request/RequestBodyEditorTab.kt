package http.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import http.BodyContentKind
import http.ExchangeFontMetrics
import http.contentTypeValueForBodyKind
import http.formatJsonBodyTextOrNull
import http.inferBodyKindFromHeaders
import http.removeContentTypeHeader
import http.upsertContentTypeHeader

@Composable
private fun BodyContentKindSelector(
    exchangeMetrics: ExchangeFontMetrics,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    enabled: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    val kind = inferBodyKindFromHeaders(headersText)
    val typeLabel = when (kind) {
        BodyContentKind.NoBody -> "无"
        BodyContentKind.FormUrlEncoded -> "表单"
        BodyContentKind.Json -> "JSON"
        BodyContentKind.Xml -> "XML"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Type: ",
            fontSize = exchangeMetrics.tab,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        )
        Box {
            TextButton(
                onClick = { showMenu = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(typeLabel, fontSize = exchangeMetrics.tab)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                listOf(
                    BodyContentKind.NoBody to "无 Body",
                    BodyContentKind.FormUrlEncoded to "表单",
                    BodyContentKind.Json to "JSON",
                    BodyContentKind.Xml to "XML",
                ).forEach { (k, label) ->
                    DropdownMenuItem(
                        onClick = {
                            showMenu = false
                            val ct = contentTypeValueForBodyKind(k)
                            if (ct != null) {
                                onHeadersTextChange(upsertContentTypeHeader(headersText, ct))
                            } else {
                                onHeadersTextChange(removeContentTypeHeader(headersText))
                            }
                        },
                    ) {
                        Text(label, fontSize = exchangeMetrics.tab, color = MaterialTheme.colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.RequestBodyEditorTab(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    bodyScrollState: ScrollState,
) {
    val bodyKind = inferBodyKindFromHeaders(headersText)
    val canFormatJson = remember(bodyText, bodyKind) {
        bodyKind == BodyContentKind.Json && formatJsonBodyTextOrNull(bodyText) != null
    }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BodyContentKindSelector(
                exchangeMetrics = exchangeMetrics,
                headersText = headersText,
                onHeadersTextChange = onHeadersTextChange,
                enabled = !isLoading,
            )
            if (bodyKind == BodyContentKind.Json) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        formatJsonBodyTextOrNull(bodyText)
                            ?.let(onBodyTextChange)
                    },
                    enabled = !isLoading && canFormatJson,
                ) {
                    Icon(
                        Icons.Filled.DataObject,
                        contentDescription = "格式化 JSON",
                        modifier = Modifier.size(20.dp),
                        tint = if (isLoading || !canFormatJson) {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                        } else {
                            MaterialTheme.colors.onSurface.copy(
                                alpha = if (isDarkTheme) 1f else ContentAlpha.high
                            )
                        },
                    )
                }
            }
        }
        Text(
            "Body（POST / PUT 等会携带）",
            fontSize = exchangeMetrics.tab,
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp, top = 4.dp, end = 10.dp)
        )
        if (bodyKind == BodyContentKind.FormUrlEncoded) {
            PlainKeyValueEditor(
                exchangeMetrics = exchangeMetrics,
                editorRequestId = editorRequestId,
                isLoading = isLoading,
                isDarkTheme = isDarkTheme,
                text = bodyText,
                onTextChange = onBodyTextChange,
                scrollState = bodyScrollState,
                hintLine = "",
                modifier = Modifier.weight(1f).fillMaxWidth(),
                defaultEditMode = KeyValueEditorMode.Form,
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        RoundedCornerShape(4.dp)
                    )
                    .background(MaterialTheme.colors.surface)
            ) {
                val cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black)
                BasicTextField(
                    value = bodyText,
                    onValueChange = onBodyTextChange,
                    enabled = !isLoading,
                    cursorBrush = cursorBrush,
                    textStyle = MaterialTheme.typography.body2.copy(
                        fontSize = exchangeMetrics.body,
                        color = MaterialTheme.colors.onSurface.copy(
                            alpha = if (!isLoading) 1f else ContentAlpha.disabled
                        )
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(bodyScrollState)
                        .padding(10.dp)
                )
            }
        }
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(bodyScrollState)
    )
}
