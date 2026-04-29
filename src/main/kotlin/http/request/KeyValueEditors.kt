package http.request

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import http.ExchangeFontMetrics
import http.filterHeaders
import http.joinHeadersEditor
import http.splitHeadersForEditor
import kotlin.math.min
import kotlinx.coroutines.delay
/** Header 表单：勾选框左右与 Key 列、容器边缘的间隔 */
private val HeaderFormCheckboxToKeyGap = 12.dp

private data class HeaderFormFocusedCell(val rowIndex: Int, val isValueColumn: Boolean)

/** 表单项之间的底部分隔：浅色虚线 */
@Composable
private fun HeaderFormRowDashedDivider(
    color: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness.coerceAtLeast(1.dp)),
    ) {
        val stroke = thickness.toPx().coerceAtLeast(1f)
        val dash = 3.dp.toPx()
        val gap = 4.dp.toPx()
        val y = size.height / 2f
        var x = 0f
        while (x < size.width) {
            val segEnd = min(x + dash, size.width)
            drawLine(
                color = color,
                start = Offset(x, y),
                end = Offset(segEnd, y),
                strokeWidth = stroke,
            )
            x += dash + gap
        }
    }
}

internal enum class KeyValueEditorMode { Text, Form }

/**
 * 「文本 / 表单」键值编辑的共用实现。
 * [enableHeaderKeySuggestions] 仅应对 **Request Headers** 为 true，以启用标准头名称的键入搜索；Body 与 Params 应为 false。
 */
@Composable
private fun KeyValueTextFormEditor(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    scrollState: ScrollState,
    hintLine: String,
    modifier: Modifier = Modifier,
    defaultEditMode: KeyValueEditorMode = KeyValueEditorMode.Text,
    enableHeaderKeySuggestions: Boolean = false,
) {
    var editMode by remember(editorRequestId, defaultEditMode) { mutableStateOf(defaultEditMode) }
    val formRows = remember { mutableStateListOf<Triple<String, String, Boolean>>() }
    val formOrphans = remember { mutableStateListOf<String>() }

    fun syncFormFromText() {
        val (valid, invalid) = splitHeadersForEditor(text)
        formRows.clear()
        formRows.addAll(valid)
        formOrphans.clear()
        formOrphans.addAll(invalid)
    }

    fun commitForm() {
        onTextChange(
            joinHeadersEditor(
                formRows.filter { it.first.isNotBlank() },
                formOrphans.toList(),
            ),
        )
    }

    // 用 DisposableEffect 在组合阶段**同步**执行：LaunchedEffect 是协程，可能晚一帧才 sync，
    // 首帧会以空的 formRows 测量/绘制，从而出现「类型是表单但表格无行、切换请求后才正常」。
    // 同时依赖 (editorRequestId, text)，以便在 id 不变、仅 setBodyText/setHeadersText/…（如导入 cURL）后立刻对齐。
    DisposableEffect(editorRequestId, text) {
        syncFormFromText()
        onDispose { }
    }

    Column(modifier = modifier.fillMaxSize().padding(end = 10.dp)) {
        if (hintLine.isNotBlank()) {
            Text(
                hintLine,
                fontSize = exchangeMetrics.compact,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp, start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "",
                fontSize = exchangeMetrics.tab,
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp)
                        .fillMaxHeight()
                        .background(
                            if (editMode == KeyValueEditorMode.Text) {
                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(enabled = !isLoading) {
                            if (editMode == KeyValueEditorMode.Form) commitForm()
                            editMode = KeyValueEditorMode.Text
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "文本",
                        fontSize = exchangeMetrics.compact,
                        color = if (editMode == KeyValueEditorMode.Text) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp)
                        .fillMaxHeight()
                        .background(
                            if (editMode == KeyValueEditorMode.Form) {
                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(enabled = !isLoading) {
                            if (editMode == KeyValueEditorMode.Text) syncFormFromText()
                            editMode = KeyValueEditorMode.Form
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "表单",
                        fontSize = exchangeMetrics.compact,
                        color = if (editMode == KeyValueEditorMode.Form) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        val outlineModifier =
            if (editMode == KeyValueEditorMode.Text) {
                Modifier.border(
                    1.dp,
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    RoundedCornerShape(4.dp),
                )
            } else {
                Modifier
            }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .then(outlineModifier)
                .background(MaterialTheme.colors.surface),
        ) {
            when (editMode) {
                KeyValueEditorMode.Text -> {
                    val cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black)
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        enabled = !isLoading,
                        cursorBrush = cursorBrush,
                        textStyle = MaterialTheme.typography.body2.copy(
                            fontSize = exchangeMetrics.body,
                            color = MaterialTheme.colors.onSurface.copy(
                                alpha = if (!isLoading) 1f else ContentAlpha.disabled,
                            ),
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(10.dp),
                    )
                }
                KeyValueEditorMode.Form -> {
                    var focusedCell by remember { mutableStateOf<HeaderFormFocusedCell?>(null) }
                    LaunchedEffect(editorRequestId) {
                        focusedCell = null
                    }
                    val dashedLineColor = MaterialTheme.colors.onSurface.copy(alpha = 0.22f)
                    val rowFieldStyle = MaterialTheme.typography.body2.copy(
                        fontSize = exchangeMetrics.compact,
                        color = MaterialTheme.colors.onSurface.copy(
                            alpha = if (!isLoading) 1f else ContentAlpha.disabled,
                        ),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            Spacer(modifier = Modifier.width(HeaderFormCheckboxToKeyGap))
                            Spacer(modifier = Modifier.width(20.dp))
                            Spacer(modifier = Modifier.width(HeaderFormCheckboxToKeyGap))
                            Text(
                                "Key",
                                fontSize = exchangeMetrics.tiny,
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                modifier = Modifier
                                    .weight(0.36f)
                                    .padding(start = 2.dp),
                            )
                            Text(
                                "Value",
                                fontSize = exchangeMetrics.tiny,
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                modifier = Modifier.weight(0.50f),
                            )
                            Spacer(modifier = Modifier.width(22.dp))
                        }
                        HeaderFormRowDashedDivider(color = dashedLineColor, thickness = 1.dp)
                        formRows.forEachIndexed { index, row ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Max),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    Spacer(modifier = Modifier.width(HeaderFormCheckboxToKeyGap))
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Checkbox(
                                            checked = row.third,
                                            onCheckedChange = { c ->
                                                formRows[index] = Triple(row.first, row.second, c)
                                                commitForm()
                                            },
                                            enabled = !isLoading,
                                            modifier = Modifier.scale(0.68f),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colors.primary,
                                                uncheckedColor = MaterialTheme.colors.onSurface.copy(
                                                    alpha = ContentAlpha.medium,
                                                ),
                                                disabledColor = MaterialTheme.colors.onSurface.copy(
                                                    alpha = ContentAlpha.disabled,
                                                ),
                                            ),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(HeaderFormCheckboxToKeyGap))
                                    val keyFocused =
                                        focusedCell == HeaderFormFocusedCell(index, isValueColumn = false)
                                    if (enableHeaderKeySuggestions) {
                                        var keyInput by remember { mutableStateOf(row.first) }
                                        var headerSuggestions by remember { mutableStateOf(emptyList<String>()) }
                                        var showSuggestions by remember { mutableStateOf(false) }
                                        var suppressSuggestionUntilNextInput by remember { mutableStateOf(false) }
                                        val keyFocusRequester = remember { FocusRequester() }

                                        LaunchedEffect(keyInput, keyFocused, suppressSuggestionUntilNextInput) {
                                            if (!keyFocused || keyInput.length < 1 || suppressSuggestionUntilNextInput) {
                                                showSuggestions = false
                                                headerSuggestions = emptyList()
                                                return@LaunchedEffect
                                            }
                                            delay(100L)
                                            headerSuggestions = filterHeaders(keyInput)
                                            showSuggestions = headerSuggestions.isNotEmpty()
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(0.36f)
                                                .fillMaxHeight()
                                                .padding(start = 2.dp, end = 4.dp)
                                                .then(
                                                    if (keyFocused) {
                                                        Modifier
                                                            .border(
                                                                2.dp,
                                                                MaterialTheme.colors.primary,
                                                                RoundedCornerShape(3.dp),
                                                            )
                                                            .padding(horizontal = 3.dp)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            BasicTextField(
                                                value = keyInput,
                                                onValueChange = { nv ->
                                                    keyInput = nv
                                                    suppressSuggestionUntilNextInput = false
                                                    formRows[index] = Triple(nv, row.second, row.third)
                                                    commitForm()
                                                },
                                                enabled = !isLoading,
                                                singleLine = true,
                                                textStyle = rowFieldStyle,
                                                cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight()
                                                    .focusRequester(keyFocusRequester)
                                                    .onFocusChanged { fc ->
                                                        if (fc.isFocused) {
                                                            focusedCell = HeaderFormFocusedCell(index, false)
                                                            keyInput = row.first
                                                            suppressSuggestionUntilNextInput = false
                                                        } else if (focusedCell == HeaderFormFocusedCell(index, false)) {
                                                            focusedCell = null
                                                            showSuggestions = false
                                                        }
                                                    },
                                                decorationBox = { innerTextField ->
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.CenterStart,
                                                    ) {
                                                        innerTextField()
                                                    }
                                                },
                                            )

                                            if (keyFocused && showSuggestions && headerSuggestions.isNotEmpty()) {
                                                val popupYOffsetPx = with(LocalDensity.current) { (28.dp * 2).roundToPx() }
                                                Popup(
                                                    offset = IntOffset(x = 0, y = popupYOffsetPx),
                                                    onDismissRequest = { showSuggestions = false },
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .background(
                                                                if (isDarkTheme) Color(0xFF1E1E1E) else Color.White,
                                                                RoundedCornerShape(4.dp),
                                                            )
                                                            .border(
                                                                1.dp,
                                                                if (isDarkTheme) Color.Gray else Color.LightGray,
                                                                RoundedCornerShape(4.dp),
                                                            )
                                                            .widthIn(max = 300.dp)
                                                            .heightIn(min = 100.dp, max = 300.dp)
                                                            .verticalScroll(rememberScrollState()),
                                                    ) {
                                                        headerSuggestions.forEach { suggestion ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable(
                                                                        interactionSource = remember { MutableInteractionSource() },
                                                                        indication = ripple(
                                                                            bounded = true,
                                                                            color = MaterialTheme.colors.primary,
                                                                        ),
                                                                    ) {
                                                                        keyInput = suggestion
                                                                        suppressSuggestionUntilNextInput = true
                                                                        formRows[index] = Triple(suggestion, row.second, row.third)
                                                                        commitForm()
                                                                        showSuggestions = false
                                                                        keyFocusRequester.requestFocus()
                                                                    }
                                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                            ) {
                                                                Text(
                                                                    text = suggestion,
                                                                    style = MaterialTheme.typography.body2,
                                                                    color = if (isDarkTheme) Color.White else Color.Black,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(0.36f)
                                                .fillMaxHeight()
                                                .padding(start = 2.dp, end = 4.dp)
                                                .then(
                                                    if (keyFocused) {
                                                        Modifier
                                                            .border(
                                                                2.dp,
                                                                MaterialTheme.colors.primary,
                                                                RoundedCornerShape(3.dp),
                                                            )
                                                            .padding(horizontal = 3.dp)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            BasicTextField(
                                                value = row.first,
                                                onValueChange = { nv ->
                                                    formRows[index] = Triple(nv, row.second, row.third)
                                                    commitForm()
                                                },
                                                enabled = !isLoading,
                                                singleLine = true,
                                                textStyle = rowFieldStyle,
                                                cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight()
                                                    .onFocusChanged { fc ->
                                                        if (fc.isFocused) {
                                                            focusedCell = HeaderFormFocusedCell(index, false)
                                                        } else if (focusedCell == HeaderFormFocusedCell(index, false)) {
                                                            focusedCell = null
                                                        }
                                                    },
                                                decorationBox = { innerTextField ->
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.CenterStart,
                                                    ) {
                                                        innerTextField()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    val valueFocused =
                                        focusedCell == HeaderFormFocusedCell(index, isValueColumn = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(0.50f)
                                            .fillMaxHeight()
                                            .padding(start = 2.dp, end = 4.dp)
                                            .then(
                                                if (valueFocused) {
                                                    Modifier
                                                        .border(
                                                            2.dp,
                                                            MaterialTheme.colors.primary,
                                                            RoundedCornerShape(3.dp),
                                                        )
                                                        .padding(horizontal = 3.dp)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        BasicTextField(
                                            value = row.second,
                                            onValueChange = { nv ->
                                                formRows[index] = Triple(row.first, nv, row.third)
                                                commitForm()
                                            },
                                            enabled = !isLoading,
                                            singleLine = true,
                                            textStyle = rowFieldStyle,
                                            cursorBrush = if (isDarkTheme) SolidColor(Color.White) else SolidColor(Color.Black),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                                .onFocusChanged { fc ->
                                                    if (fc.isFocused) {
                                                        focusedCell = HeaderFormFocusedCell(index, true)
                                                    } else if (focusedCell == HeaderFormFocusedCell(index, true)) {
                                                        focusedCell = null
                                                    }
                                                },
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.CenterStart,
                                                ) {
                                                    innerTextField()
                                                }
                                            },
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        IconButton(
                                            onClick = {
                                                formRows.removeAt(index)
                                                commitForm()
                                            },
                                            enabled = !isLoading,
                                            modifier = Modifier.size(22.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "删除此行",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colors.onSurface.copy(
                                                    alpha = if (!isLoading) {
                                                        ContentAlpha.medium
                                                    } else {
                                                        ContentAlpha.disabled
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                }
                                HeaderFormRowDashedDivider(color = dashedLineColor, thickness = 1.dp)
                            }
                        }
                        TextButton(
                            onClick = { formRows.add(Triple("", "", true)) },
                            enabled = !isLoading,
                            modifier = Modifier.align(Alignment.Start),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colors.primary,
                            )
                            Text(
                                "添加",
                                fontSize = exchangeMetrics.compact,
                                color = MaterialTheme.colors.onSurface,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun PlainKeyValueEditor(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    scrollState: ScrollState,
    hintLine: String,
    modifier: Modifier = Modifier,
    defaultEditMode: KeyValueEditorMode = KeyValueEditorMode.Text,
) {
    KeyValueTextFormEditor(
        exchangeMetrics = exchangeMetrics,
        editorRequestId = editorRequestId,
        isLoading = isLoading,
        isDarkTheme = isDarkTheme,
        text = text,
        onTextChange = onTextChange,
        scrollState = scrollState,
        hintLine = hintLine,
        modifier = modifier,
        defaultEditMode = defaultEditMode,
        enableHeaderKeySuggestions = false,
    )
}

/** Request Headers 键值编辑：Key 列带标准头名称搜索。 */
@Composable
internal fun HeadersKeyValueEditor(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    isDarkTheme: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    scrollState: ScrollState,
    hintLine: String,
    modifier: Modifier = Modifier,
    defaultEditMode: KeyValueEditorMode = KeyValueEditorMode.Text,
) {
    KeyValueTextFormEditor(
        exchangeMetrics = exchangeMetrics,
        editorRequestId = editorRequestId,
        isLoading = isLoading,
        isDarkTheme = isDarkTheme,
        text = text,
        onTextChange = onTextChange,
        scrollState = scrollState,
        hintLine = hintLine,
        modifier = modifier,
        defaultEditMode = defaultEditMode,
        enableHeaderKeySuggestions = true,
    )
}
