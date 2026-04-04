package http

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 顶栏：主题、设置、从剪贴板导入 cURL（图标按钮，全宽；与下方左右分栏组成 T 形布局） */
@Composable
fun RequestTopBar(
    isLoading: Boolean,
    isDarkTheme: Boolean,
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    onHeadersTextChange: (String) -> Unit
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
                .height(UrlBarCompactHeight),
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
                        Text(method, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = methodMenuExpanded,
                        onDismissRequest = { onMethodMenuExpandedChange(false) }
                    ) {
                        DropdownMenuItem(onClick = {
                            onMethodSelected("GET")
                            onMethodMenuExpandedChange(false)
                        }) {
                            Text("GET", fontSize = 12.sp, color = MaterialTheme.colors.onSurface)
                        }
                        DropdownMenuItem(onClick = {
                            onMethodSelected("POST")
                            onMethodMenuExpandedChange(false)
                        }) {
                            Text("POST", fontSize = 12.sp, color = MaterialTheme.colors.onSurface)
                        }
                    }
                }
                UrlInputWithInlineSend(
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
            editorRequestId = editorRequestId,
            isLoading = isLoading,
            leftTabIndex = leftTabIndex,
            onLeftTabIndexChange = onLeftTabIndexChange,
            bodyText = bodyText,
            onBodyTextChange = onBodyTextChange,
            headersText = headersText,
            onHeadersTextChange = onHeadersTextChange
        )
    }
}

/** 左侧 Body / Headers 编辑区（分割条左侧） */
private enum class HeadersEditMode { Text, Form }

@Composable
private fun headerFormOutlinedColors(enabled: Boolean) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = MaterialTheme.colors.onSurface.copy(
        alpha = if (enabled) 1f else ContentAlpha.disabled
    ),
    disabledTextColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
    backgroundColor = Color.Transparent,
    cursorColor = MaterialTheme.colors.primary,
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
    disabledBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
)

@Composable
fun RequestEditorPane(
    modifier: Modifier = Modifier,
    editorRequestId: String?,
    isLoading: Boolean,
    leftTabIndex: Int,
    onLeftTabIndexChange: (Int) -> Unit,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
    headersText: String,
    onHeadersTextChange: (String) -> Unit
) {
    val bodyScrollState = rememberScrollState()
    val headersScrollState = rememberScrollState()
    var headersEditMode by remember { mutableStateOf(HeadersEditMode.Text) }
    val formHeaderRows = remember { mutableStateListOf<Triple<String, String, Boolean>>() }
    val formHeaderOrphans = remember { mutableStateListOf<String>() }

    fun syncFormFromHeadersText() {
        val (valid, invalid) = splitHeadersForEditor(headersText)
        formHeaderRows.clear()
        formHeaderRows.addAll(valid)
        formHeaderOrphans.clear()
        formHeaderOrphans.addAll(invalid)
    }

    fun commitHeadersForm() {
        onHeadersTextChange(
            joinHeadersEditor(
                formHeaderRows.filter { it.first.isNotBlank() },
                formHeaderOrphans.toList()
            )
        )
    }

    LaunchedEffect(editorRequestId) {
        syncFormFromHeadersText()
    }

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
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
                    fontSize = 12.sp,
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
                    fontSize = 12.sp,
                    color = if (leftTabIndex == 1) {
                        MaterialTheme.colors.onSurface
                    } else {
                        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                    }
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
                            "Body（POST 时生效）",
                            fontSize = 12.sp,
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
                                    fontSize = 13.sp,
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
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp, start = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                when (headersEditMode) {
                                    HeadersEditMode.Text ->
                                        "每行 Key: Value；行首「! 」表示不发送；无效行发送时不带"
                                    HeadersEditMode.Form ->
                                        "勾选=发送；取消勾选或文本模式「! 」前缀均不发送；无效行请用文本查看"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                modifier = Modifier
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp)
                                        .fillMaxHeight()
                                        .background(
                                            if (headersEditMode == HeadersEditMode.Text) {
                                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = !isLoading) {
                                            if (headersEditMode == HeadersEditMode.Form) {
                                                commitHeadersForm()
                                            }
                                            headersEditMode = HeadersEditMode.Text
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "文本",
                                        fontSize = 11.sp,
                                        color = if (headersEditMode == HeadersEditMode.Text) {
                                            MaterialTheme.colors.onSurface
                                        } else {
                                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp)
                                        .fillMaxHeight()
                                        .background(
                                            if (headersEditMode == HeadersEditMode.Form) {
                                                MaterialTheme.colors.primary.copy(alpha = 0.14f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = !isLoading) {
                                            if (headersEditMode == HeadersEditMode.Text) {
                                                syncFormFromHeadersText()
                                            }
                                            headersEditMode = HeadersEditMode.Form
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "表单",
                                        fontSize = 11.sp,
                                        color = if (headersEditMode == HeadersEditMode.Form) {
                                            MaterialTheme.colors.onSurface
                                        } else {
                                            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
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
                            when (headersEditMode) {
                                HeadersEditMode.Text -> {
                                    BasicTextField(
                                        value = headersText,
                                        onValueChange = onHeadersTextChange,
                                        enabled = !isLoading,
                                        textStyle = MaterialTheme.typography.body2.copy(
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colors.onSurface.copy(
                                                alpha = if (!isLoading) 1f else ContentAlpha.disabled
                                            )
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(headersScrollState)
                                            .padding(10.dp)
                                    )
                                }
                                HeadersEditMode.Form -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(headersScrollState)
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Spacer(modifier = Modifier.width(24.dp))
                                            Text(
                                                "Key",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                                modifier = Modifier.weight(0.36f)
                                            )
                                            Text(
                                                "Value",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                                modifier = Modifier.weight(0.50f)
                                            )
                                            Spacer(modifier = Modifier.width(26.dp))
                                        }
                                        formHeaderRows.forEachIndexed { index, row ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.width(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Checkbox(
                                                        checked = row.third,
                                                        onCheckedChange = { c ->
                                                            formHeaderRows[index] =
                                                                Triple(row.first, row.second, c)
                                                            commitHeadersForm()
                                                        },
                                                        enabled = !isLoading,
                                                        modifier = Modifier.scale(0.78f),
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = MaterialTheme.colors.primary,
                                                            uncheckedColor = MaterialTheme.colors.onSurface.copy(
                                                                alpha = ContentAlpha.medium
                                                            ),
                                                            disabledColor = MaterialTheme.colors.onSurface.copy(
                                                                alpha = ContentAlpha.disabled
                                                            )
                                                        )
                                                    )
                                                }
                                                OutlinedTextField(
                                                    value = row.first,
                                                    onValueChange = { nv ->
                                                        formHeaderRows[index] =
                                                            Triple(nv, row.second, row.third)
                                                        commitHeadersForm()
                                                    },
                                                    enabled = !isLoading,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.body2.copy(
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colors.onSurface.copy(
                                                            alpha = if (!isLoading) 1f else ContentAlpha.disabled
                                                        )
                                                    ),
                                                    colors = headerFormOutlinedColors(!isLoading),
                                                    modifier = Modifier
                                                        .weight(0.36f)
                                                        .defaultMinSize(minHeight = 36.dp)
                                                )
                                                OutlinedTextField(
                                                    value = row.second,
                                                    onValueChange = { nv ->
                                                        formHeaderRows[index] =
                                                            Triple(row.first, nv, row.third)
                                                        commitHeadersForm()
                                                    },
                                                    enabled = !isLoading,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.body2.copy(
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colors.onSurface.copy(
                                                            alpha = if (!isLoading) 1f else ContentAlpha.disabled
                                                        )
                                                    ),
                                                    colors = headerFormOutlinedColors(!isLoading),
                                                    modifier = Modifier
                                                        .weight(0.50f)
                                                        .defaultMinSize(minHeight = 36.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        formHeaderRows.removeAt(index)
                                                        commitHeadersForm()
                                                    },
                                                    enabled = !isLoading,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "删除此行",
                                                        modifier = Modifier.size(15.dp),
                                                        tint = MaterialTheme.colors.onSurface.copy(
                                                            alpha = if (!isLoading) ContentAlpha.medium else ContentAlpha.disabled
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                formHeaderRows.add(Triple("", "", true))
                                            },
                                            enabled = !isLoading,
                                            modifier = Modifier.align(Alignment.Start),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colors.primary
                                            )
                                            Text(
                                                "添加",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colors.onSurface,
                                                modifier = Modifier.padding(start = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(headersScrollState)
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
            .height(UrlBarCompactHeight)
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
                fontSize = 13.sp,
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
                                fontSize = 13.sp,
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

/** URL 栏与两侧按钮对齐的高度；栏内用 BasicTextField 避免默认内边距裁切文字 */
val UrlBarCompactHeight = 39.dp

/** 左侧 URL 行高度，右侧状态区用相同高度以便 Body/Headers Tab 与左侧对齐 */
val RequestSideTopChromeTotalHeight = UrlBarCompactHeight
