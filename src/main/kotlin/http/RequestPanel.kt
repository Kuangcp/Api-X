package http

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material.TextButton
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import app.EnvironmentsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

private val requestMethodDropdownChoices = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")

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

private enum class KeyValueEditorMode { Text, Form }

/** 与 Headers 相同的「文本 / 表单」键值编辑（Params 复用）。 */
@Composable
private fun HeaderLikeKeyValueEditor(
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    scrollState: ScrollState,
    hintLine: String,
    modifier: Modifier = Modifier,
) {
    var editMode by remember { mutableStateOf(KeyValueEditorMode.Text) }
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

    LaunchedEffect(editorRequestId) {
        syncFormFromText()
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
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        enabled = !isLoading,
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
                                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
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
                                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
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

/** 顶栏：环境、主题、设置、从剪贴板导入 cURL（图标按钮，全宽；与下方左右分栏组成 T 形布局） */
@Composable
fun RequestTopBar(
    isLoading: Boolean,
    isDarkTheme: Boolean,
    environmentsState: EnvironmentsState,
    onActiveEnvironmentChange: (String?) -> Unit,
    onManageEnvironmentsClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onImportCurlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topBarIconTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
    val topBarIconButtonModifier = Modifier
        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
        .size(26.dp)
    val topBarIconModifier = Modifier.size(17.dp)
    var envMenuExpanded by remember { mutableStateOf(false) }
    val activeLabel = environmentsState.activeEnvironment()?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: "无环境"
    val envButtonText = "环境: $activeLabel"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(
                    onClick = { envMenuExpanded = true },
                    enabled = !isLoading,
                    modifier = Modifier.widthIn(max = 200.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            envButtonText,
                            maxLines = 1,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high),
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = topBarIconTint,
                        )
                    }
                }
                DropdownMenu(
                    expanded = envMenuExpanded,
                    onDismissRequest = { envMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        onClick = {
                            onActiveEnvironmentChange(null)
                            envMenuExpanded = false
                        },
                    ) {
                        Text("无环境")
                    }
                    if (environmentsState.environments.isNotEmpty()) {
                        Divider()
                        for (env in environmentsState.environments) {
                            DropdownMenuItem(
                                onClick = {
                                    onActiveEnvironmentChange(env.id)
                                    envMenuExpanded = false
                                },
                            ) {
                                Text(env.name.ifBlank { "(未命名)" })
                            }
                        }
                    }
                    Divider()
                    DropdownMenuItem(
                        onClick = {
                            envMenuExpanded = false
                            onManageEnvironmentsClick()
                        },
                    ) {
                        Text("管理环境…")
                    }
                }
            }
            IconButton(
                onClick = onThemeToggle,
                enabled = !isLoading,
                modifier = topBarIconButtonModifier
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDarkTheme) "切换浅色主题" else "切换深色主题",
                    modifier = topBarIconModifier,
                    tint = topBarIconTint
                )
            }
            IconButton(
                onClick = onSettingsClick,
                enabled = !isLoading,
                modifier = topBarIconButtonModifier
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    modifier = topBarIconModifier,
                    tint = topBarIconTint
                )
            }
            IconButton(
                onClick = onImportCurlClick,
                enabled = !isLoading,
                modifier = topBarIconButtonModifier
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = "从剪贴板导入 cURL",
                    modifier = topBarIconModifier,
                    tint = topBarIconTint
                )
            }
        }
        Divider(
            modifier = Modifier.padding(top = 2.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        )
    }
}

/**
 * 左侧整栏：方法/URL/发送 + Body/Headers 编辑区（与右侧响应区对称分栏；顶栏由 [RequestTopBar] 单独提供）。
 */
@Composable
fun RequestSidePanel(
    modifier: Modifier = Modifier,
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    method: String,
    methodMenuExpanded: Boolean,
    onMethodMenuExpandedChange: (Boolean) -> Unit,
    onMethodSelected: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    onSendOrCancel: () -> Unit,
    leftTabIndex: Int,
    onLeftTabIndexChange: (Int) -> Unit,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    paramsText: String,
    onParamsTextChange: (String) -> Unit,
) {
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
                        onClick = { onMethodMenuExpandedChange(true) },
                        enabled = !isLoading,
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = methodButtonColors
                    ) {
                        Text(method, fontSize = exchangeMetrics.tab)
                    }
                    DropdownMenu(
                        expanded = methodMenuExpanded,
                        onDismissRequest = { onMethodMenuExpandedChange(false) }
                    ) {
                        for (m in requestMethodDropdownChoices) {
                            DropdownMenuItem(onClick = {
                                onMethodSelected(m)
                                onMethodMenuExpandedChange(false)
                            }) {
                                Text(m, fontSize = exchangeMetrics.tab, color = MaterialTheme.colors.onSurface)
                            }
                        }
                    }
                }
                UrlInputWithInlineSend(
                    exchangeMetrics = exchangeMetrics,
                    url = url,
                    onUrlChange = onUrlChange,
                    urlFieldEnabled = !isLoading,
                    isLoading = isLoading,
                    onSendOrCancel = onSendOrCancel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RequestEditorPane(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            exchangeMetrics = exchangeMetrics,
            editorRequestId = editorRequestId,
            isLoading = isLoading,
            leftTabIndex = leftTabIndex,
            onLeftTabIndexChange = onLeftTabIndexChange,
            bodyText = bodyText,
            onBodyTextChange = onBodyTextChange,
            headersText = headersText,
            onHeadersTextChange = onHeadersTextChange,
            paramsText = paramsText,
            onParamsTextChange = onParamsTextChange,
        )
    }
}

/** 左侧 Body / Headers / Params 编辑区（分割条左侧） */
@Composable
fun RequestEditorPane(
    modifier: Modifier = Modifier,
    exchangeMetrics: ExchangeFontMetrics,
    editorRequestId: String?,
    isLoading: Boolean,
    leftTabIndex: Int,
    onLeftTabIndexChange: (Int) -> Unit,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
    headersText: String,
    onHeadersTextChange: (String) -> Unit,
    paramsText: String,
    onParamsTextChange: (String) -> Unit,
) {
    val bodyScrollState = rememberScrollState()
    val headersScrollState = rememberScrollState()
    val paramsScrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(exchangeMetrics.editorTabStripHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (leftTabIndex == 0) {
                            MaterialTheme.colors.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable(enabled = !isLoading) { onLeftTabIndexChange(0) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Body",
                    fontSize = exchangeMetrics.tab,
                    color = if (leftTabIndex == 0) {
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
                        if (leftTabIndex == 1) {
                            MaterialTheme.colors.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable(enabled = !isLoading) { onLeftTabIndexChange(1) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Headers",
                    fontSize = exchangeMetrics.tab,
                    color = if (leftTabIndex == 1) {
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
                        if (leftTabIndex == 2) {
                            MaterialTheme.colors.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(enabled = !isLoading) { onLeftTabIndexChange(2) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Params",
                    fontSize = exchangeMetrics.tab,
                    color = if (leftTabIndex == 2) {
                        MaterialTheme.colors.onSurface
                    } else {
                        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                    },
                )
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            "Body（POST / PUT 等会携带）",
                            fontSize = exchangeMetrics.tab,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                    RoundedCornerShape(4.dp)
                                )
                                .background(MaterialTheme.colors.surface)
                        ) {
                            BasicTextField(
                                value = bodyText,
                                onValueChange = onBodyTextChange,
                                enabled = !isLoading,
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
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(bodyScrollState)
                    )
                }
                1 -> {
                    HeaderLikeKeyValueEditor(
                        exchangeMetrics = exchangeMetrics,
                        editorRequestId = editorRequestId,
                        isLoading = isLoading,
                        text = headersText,
                        onTextChange = onHeadersTextChange,
                        scrollState = headersScrollState,
                        hintLine = "",
                        modifier = Modifier.fillMaxSize(),
                    )
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(headersScrollState),
                    )
                }
                2 -> {
                    HeaderLikeKeyValueEditor(
                        exchangeMetrics = exchangeMetrics,
                        editorRequestId = editorRequestId,
                        isLoading = isLoading,
                        text = paramsText,
                        onTextChange = onParamsTextChange,
                        scrollState = paramsScrollState,
                        hintLine = "可与 URL 栏内已有 ?query 用 & 合并。",
                        modifier = Modifier.fillMaxSize(),
                    )
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(paramsScrollState),
                    )
                }
            }
        }
    }
}

/**
 * URL 输入与发送/取消合成一块：外层圆角描边，输入区无边框，右侧小图标形似嵌在输入框内。
 */
@Composable
private fun UrlInputWithInlineSend(
    exchangeMetrics: ExchangeFontMetrics,
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
