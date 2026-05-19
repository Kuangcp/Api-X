@file:OptIn(ExperimentalSerializationApi::class)

package http.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import com.neoutils.highlight.compose.remember.rememberHighlight
import com.neoutils.highlight.compose.remember.rememberTextFieldValue
import com.neoutils.highlight.core.extension.textColor
import com.neoutils.highlight.core.util.UiColor
import app.EnvVariable
import http.BodyContentKind
import http.ExchangeFontMetrics
import http.contentTypeValueForBodyKind
import http.inferBodyKindFromHeaders
import http.removeContentTypeHeader
import http.upsertContentTypeHeader

private fun Color.toUiColor(): UiColor {
    val r = (this.red * 255).toInt()
    val g = (this.green * 255).toInt()
    val b = (this.blue * 255).toInt()
    val a = (this.alpha * 255).toInt()
    val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
    return UiColor.Integer(argb)
}

private val prettyPrintJson = Json { 
    prettyPrint = true 
    prettyPrintIndent = "  " 
    ignoreUnknownKeys = true
}

@Composable
private fun BodyContentKindSelector(
    exchangeMetrics: ExchangeFontMetrics,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    enabled: Boolean,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
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
        Spacer(Modifier.weight(1f))
        if (kind == BodyContentKind.Json) {
            IconButton(
                onClick = {
                    try {
                        val element = Json.parseToJsonElement(bodyText)
                        val pretty = prettyPrintJson.encodeToString(JsonElement.serializer(), element)
                        onBodyTextChange(pretty)
                    } catch (_: Exception) {
                    }
                },
                enabled = enabled && bodyText.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = "格式化 JSON",
                    tint = MaterialTheme.colors.onSurface.copy(alpha = if (enabled) 0.7f else ContentAlpha.disabled),
                )
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
    envVars: List<EnvVariable> = emptyList(),
) {
    val bodyKind = inferBodyKindFromHeaders(headersText)
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
                bodyText = bodyText,
                onBodyTextChange = onBodyTextChange,
            )
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
                envVars = envVars,
            )
        } else {
            var showBodyEnvVars by remember { mutableStateOf(false) }
            var bodyEnvFilter by remember { mutableStateOf("") }
            val bodyEnvTrigger = detectEnvVarTrigger(bodyText)
            LaunchedEffect(bodyEnvTrigger) {
                if (bodyEnvTrigger != null) {
                    bodyEnvFilter = bodyEnvTrigger
                    showBodyEnvVars = true
                } else {
                    showBodyEnvVars = false
                }
            }

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
                if (bodyKind == BodyContentKind.Json) {
                    JsonEditor(
                        text = bodyText,
                        onTextChange = onBodyTextChange,
                        isDarkTheme = isDarkTheme,
                        isLoading = isLoading,
                        exchangeMetrics = exchangeMetrics,
                        bodyScrollState = bodyScrollState,
                    )
                } else {
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

                if (showBodyEnvVars && envVars.isNotEmpty()) {
                    EnvVarAutocompletePopup(
                        filterText = bodyEnvFilter,
                        envVars = envVars,
                        isDarkTheme = isDarkTheme,
                        exchangeMetrics = exchangeMetrics,
                        onSelect = { varName ->
                            onBodyTextChange(applyEnvVarSelection(bodyText, varName))
                            showBodyEnvVars = false
                        },
                        onDismissRequest = { showBodyEnvVars = false },
                    )
                }
            }
        }
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(bodyScrollState)
    )
}

@Composable
private fun JsonEditor(
    text: String,
    onTextChange: (String) -> Unit,
    isDarkTheme: Boolean,
    isLoading: Boolean,
    exchangeMetrics: ExchangeFontMetrics,
    bodyScrollState: ScrollState,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }

    LaunchedEffect(text) {
        if (textFieldValue.text != text) {
            textFieldValue = TextFieldValue(text)
        }
    }

    val stringColor = if (isDarkTheme) Color(0xFFCE9178) else Color(0xFFA31515)
    val keyColor = if (isDarkTheme) Color(0xFF9CDCFE) else Color(0xFF0451A5)
    val numberColor = if (isDarkTheme) Color(0xFFB5CEA8) else Color(0xFF098658)
    val keywordColor = if (isDarkTheme) Color(0xFF569CD6) else Color(0xFF0000FF)
    val punctuationColor = if (isDarkTheme) Color(0xFFD4D4D4) else Color(0xFF242424)

    val highlightedValue = rememberHighlight {
        textColor { "\"[^\"\\\\]*\"(?=\\s*:)".toRegex().fully(keyColor.toUiColor()) }
        textColor { "\"[^\"\\\\]*\"(?![:\\s])".toRegex().fully(stringColor.toUiColor()) }
        textColor { "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?".toRegex().fully(numberColor.toUiColor()) }
        textColor { "\\b(true|false|null)\\b".toRegex().fully(keywordColor.toUiColor()) }
        textColor { "[{}\\[\\]:,]".toRegex().fully(punctuationColor.toUiColor()) }
    }.rememberTextFieldValue(textFieldValue)

    BasicTextField(
        value = highlightedValue.copy(composition = textFieldValue.composition),
        onValueChange = { newValue ->
            textFieldValue = newValue
            onTextChange(newValue.text)
        },
        enabled = !isLoading,
        cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black),
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
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