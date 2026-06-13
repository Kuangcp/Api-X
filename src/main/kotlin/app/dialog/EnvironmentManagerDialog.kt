package app.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.Typography
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import app.settings.Environment
import app.settings.EnvironmentStore
import app.settings.EnvironmentsState
import app.settings.EnvVariable
import app.ui.appMaterialColors
import java.util.UUID
import kotlin.math.min

@Composable
fun EnvironmentManagerDialogWindow(
    visible: Boolean,
    isDarkTheme: Boolean,
    appBackgroundHex: String,
    typographyBase: Typography,
    initial: EnvironmentsState,
    onCloseRequest: () -> Unit,
    onSaved: (EnvironmentsState) -> Unit,
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "环境变量",
        state = rememberDialogState(width = 720.dp, height = 640.dp),
    ) {
        val colors = appMaterialColors(isDarkTheme, appBackgroundHex)
        MaterialTheme(
            colors = colors,
            typography = typographyBase,
        ) {
            EnvironmentManagerDialogBody(
                initial = initial,
                onCancel = onCloseRequest,
                onSave = {
                    onSaved(it)
                    onCloseRequest()
                },
            )
        }
    }
}

@Composable
private fun EnvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = MaterialTheme.colors.onSurface,
            disabledTextColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
            cursorColor = MaterialTheme.colors.primary,
            focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
            disabledBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
            placeholderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            disabledPlaceholderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
            focusedLabelColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
            unfocusedLabelColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            disabledLabelColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
        )
    )
}

@Composable
private fun DashedDivider(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp),
    ) {
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
                strokeWidth = 1f,
            )
            x += dash + gap
        }
    }
}

@Composable
private fun EnvironmentManagerDialogBody(
    initial: EnvironmentsState,
    onCancel: () -> Unit,
    onSave: (EnvironmentsState) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial.normalized()) }
    var selectedId by remember(initial) {
        mutableStateOf(
            initial.activeEnvironmentId
                ?: initial.environments.firstOrNull()?.id,
        )
    }
    val variableRows = remember { mutableStateListOf<EnvVariable>() }
    var envNameEdit by remember { mutableStateOf("") }

    LaunchedEffect(selectedId) {
        variableRows.clear()
        val id = selectedId
        if (id == null) {
            envNameEdit = ""
            return@LaunchedEffect
        }
        val env = draft.environments.find { it.id == id }
        if (env == null) {
            envNameEdit = ""
            return@LaunchedEffect
        }
        variableRows.addAll(env.variables)
        envNameEdit = env.name
    }

    fun persistCurrentEnvIntoDraft(): EnvironmentsState {
        val sid = selectedId ?: return draft
        val idx = draft.environments.indexOfFirst { it.id == sid }
        if (idx < 0) return draft
        val old = draft.environments[idx]
        val updated = old.copy(
            name = envNameEdit.trim().ifBlank { old.name },
            variables = variableRows.toList(),
        )
        val list = draft.environments.toMutableList()
        list[idx] = updated
        return draft.copy(environments = list)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
    ) {
        Text(
            "使用 {{变量名}} 作为占位符（如 {{host}}）。发送请求时按当前选中的环境替换；请求正文仍保存未解析的原文。",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        draft = persistCurrentEnvIntoDraft()
                        val id = UUID.randomUUID().toString()
                        val n = draft.environments.size + 1
                        draft = draft.copy(
                            environments = draft.environments + Environment(
                                id = id,
                                name = "环境 $n",
                                variables = emptyList(),
                            ),
                            activeEnvironmentId = id,
                        )
                        selectedId = id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("新建环境")
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                var draggedItemId by remember { mutableStateOf<String?>(null) }
                var dragTotalOffset by remember { mutableStateOf(0f) }
                val itemHeight = 30.dp
                val density = LocalDensity.current
                val itemHeightPx = with(density) { itemHeight.toPx() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for ((index, env) in draft.environments.withIndex()) {
                        key(env.id) {
                            val isDragged = draggedItemId == env.id
                            val isSelected = env.id == selectedId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight)
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                MaterialTheme.colors.primary.copy(alpha = 0.10f),
                                                RoundedCornerShape(6.dp),
                                            )
                                        } else Modifier
                                    )
                                    .offset(
                                        y = if (isDragged) with(density) { dragTotalOffset.toDp() } else 0.dp,
                                    )
                                    .clickable(onClick = {
                                        draft = persistCurrentEnvIntoDraft()
                                        selectedId = env.id
                                    })
                                    .padding(start = 2.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .fillMaxHeight()
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedItemId = env.id
                                                    dragTotalOffset = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragTotalOffset += dragAmount.y
                                                    val id = draggedItemId ?: return@detectDragGestures
                                                    val ci = draft.environments.indexOfFirst { it.id == id }
                                                    if (ci < 0) return@detectDragGestures
                                                    if (dragTotalOffset > itemHeightPx && ci < draft.environments.lastIndex) {
                                                        val list = draft.environments.toMutableList()
                                                        val tmp = list[ci]
                                                        list[ci] = list[ci + 1]
                                                        list[ci + 1] = tmp
                                                        draft = draft.copy(environments = list)
                                                        dragTotalOffset -= itemHeightPx
                                                    } else if (dragTotalOffset < -itemHeightPx && ci > 0) {
                                                        val list = draft.environments.toMutableList()
                                                        val tmp = list[ci]
                                                        list[ci] = list[ci - 1]
                                                        list[ci - 1] = tmp
                                                        draft = draft.copy(environments = list)
                                                        dragTotalOffset += itemHeightPx
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedItemId = null
                                                    dragTotalOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggedItemId = null
                                                    dragTotalOffset = 0f
                                                },
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Menu,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.28f),
                                    )
                                }
                                val nameColor =
                                    if (isSelected) MaterialTheme.colors.primary
                                    else MaterialTheme.colors.onSurface
                                Text(
                                    env.name.ifBlank { "(未命名)" },
                                    modifier = Modifier.weight(1f),
                                    color = nameColor,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.body2,
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        val sid = selectedId ?: return@TextButton
                        draft = persistCurrentEnvIntoDraft()
                        val nextList = draft.environments.filter { it.id != sid }
                        var nextActive = draft.activeEnvironmentId
                        if (nextActive == sid) nextActive = nextList.firstOrNull()?.id
                        draft = draft.copy(environments = nextList, activeEnvironmentId = nextActive)
                        selectedId = nextList.firstOrNull()?.id
                    },
                    enabled = selectedId != null && draft.environments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除当前环境")
                }
            }
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selectedId == null) {
                    Text(
                        "请新建或选择一个环境",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
                    )
                } else {
                    EnvTextField(
                        value = envNameEdit,
                        onValueChange = { envNameEdit = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("环境名称") },
                        singleLine = true,
                    )
                    Text(
                        "变量",
                        style = MaterialTheme.typography.subtitle2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                    val rowFieldStyle = MaterialTheme.typography.body2.copy(
                        color = MaterialTheme.colors.onSurface,
                    )
                    val dashedLineColor = MaterialTheme.colors.onSurface.copy(alpha = 0.22f)
                    var focusedKeyCell by remember { mutableStateOf<Int?>(null) }
                    var focusedValueCell by remember { mutableStateOf<Int?>(null) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = 120.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "Key",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                modifier = Modifier
                                    .weight(0.42f)
                                    .padding(start = 2.dp),
                            )
                            Text(
                                "Value",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                modifier = Modifier.weight(0.58f),
                            )
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                        DashedDivider(color = dashedLineColor)
                        for (i in variableRows.indices) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val isKeyFocused = focusedKeyCell == i
                                    Box(
                                        modifier = Modifier
                                            .weight(0.42f)
                                            .fillMaxHeight()
                                            .padding(start = 2.dp, end = 4.dp)
                                            .then(
                                                if (isKeyFocused) {
                                                    Modifier
                                                        .border(
                                                            2.dp,
                                                            MaterialTheme.colors.primary,
                                                            RoundedCornerShape(3.dp),
                                                        )
                                                        .padding(horizontal = 3.dp)
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        BasicTextField(
                                            value = variableRows[i].key,
                                            onValueChange = { v ->
                                                variableRows[i] = variableRows[i].copy(key = v)
                                            },
                                            singleLine = true,
                                            textStyle = rowFieldStyle,
                                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                                .onFocusChanged { fc ->
                                                    if (fc.isFocused) {
                                                        focusedKeyCell = i
                                                    } else if (focusedKeyCell == i) {
                                                        focusedKeyCell = null
                                                    }
                                                },
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.CenterStart,
                                                ) {
                                                    if (variableRows[i].key.isEmpty() && !isKeyFocused) {
                                                        Text(
                                                            "host",
                                                            style = MaterialTheme.typography.body2,
                                                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                        )
                                    }
                                    val isValueFocused = focusedValueCell == i
                                    Box(
                                        modifier = Modifier
                                            .weight(0.58f)
                                            .fillMaxHeight()
                                            .padding(start = 2.dp, end = 4.dp)
                                            .then(
                                                if (isValueFocused) {
                                                    Modifier
                                                        .border(
                                                            2.dp,
                                                            MaterialTheme.colors.primary,
                                                            RoundedCornerShape(3.dp),
                                                        )
                                                        .padding(horizontal = 3.dp)
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        BasicTextField(
                                            value = variableRows[i].value,
                                            onValueChange = { v ->
                                                variableRows[i] = variableRows[i].copy(value = v)
                                            },
                                            singleLine = true,
                                            textStyle = rowFieldStyle,
                                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                                .onFocusChanged { fc ->
                                                    if (fc.isFocused) {
                                                        focusedValueCell = i
                                                    } else if (focusedValueCell == i) {
                                                        focusedValueCell = null
                                                    }
                                                },
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.CenterStart,
                                                ) {
                                                    if (variableRows[i].value.isEmpty() && !isValueFocused) {
                                                        Text(
                                                            "https://api.example.com",
                                                            style = MaterialTheme.typography.body2,
                                                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        IconButton(
                                            onClick = { variableRows.removeAt(i) },
                                            modifier = Modifier.size(22.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                            )
                                        }
                                    }
                                }
                                DashedDivider(color = dashedLineColor)
                            }
                        }
                        TextButton(
                            onClick = { variableRows.add(EnvVariable()) },
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
                                color = MaterialTheme.colors.onSurface,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            TextButton(
                onClick = {
                    val merged = persistCurrentEnvIntoDraft().normalized()
                    onSave(merged)
                },
            ) {
                Text("保存")
            }
        }
    }
}
