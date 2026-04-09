package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import db.CollectionRepository
import kotlinx.coroutines.delay
import tree.GlobalSearchRequestRow
import tree.UiCollection
import tree.requestParentLocationLabel

@Composable
fun GlobalSearchDialogWindow(
    visible: Boolean,
    isDarkTheme: Boolean,
    appBackgroundHex: String,
    typographyBase: Typography,
    tree: List<UiCollection>,
    repository: CollectionRepository,
    onCloseRequest: () -> Unit,
    onPickRequest: (String) -> Unit,
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "全局搜索",
        state = rememberDialogState(width = 560.dp, height = 480.dp),
    ) {
        val colors = appMaterialColors(isDarkTheme, appBackgroundHex)
        MaterialTheme(
            colors = colors,
            typography = typographyBase,
        ) {
            GlobalSearchDialogBody(
                tree = tree,
                repository = repository,
                onCloseRequest = onCloseRequest,
                onPickRequest = onPickRequest,
            )
        }
    }
}

@Composable
private fun GlobalSearchDialogBody(
    tree: List<UiCollection>,
    repository: CollectionRepository,
    onCloseRequest: () -> Unit,
    onPickRequest: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<GlobalSearchRequestRow>>(emptyList()) }
    var selectedResultIndex by remember { mutableIntStateOf(-1) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val defaultRowHeightPx = remember(density) {
        with(density) { 68.dp.roundToPx() }.coerceAtLeast(24)
    }

    LaunchedEffect(tree) {
        rows = repository.loadAllRequestsForGlobalSearch()
    }

    LaunchedEffect(Unit) {
        delay(50)
        focusRequester.requestFocus()
    }

    val filtered by remember(rows, query) {
        derivedStateOf {
            val q = query.trim()
            if (q.isEmpty()) emptyList()
            else rows.filter {
                matchesGlobalSearchQuery(q, it.name, it.url, it.headersText, it.bodyText)
            }
        }
    }

    LaunchedEffect(query) {
        selectedResultIndex = -1
    }

    LaunchedEffect(filtered) {
        if (filtered.isEmpty()) {
            selectedResultIndex = -1
        } else if (selectedResultIndex >= filtered.size) {
            selectedResultIndex = filtered.lastIndex
        }
    }

    LaunchedEffect(selectedResultIndex, filtered.size) {
        if (selectedResultIndex < 0 || filtered.isEmpty()) return@LaunchedEffect
        val idx = selectedResultIndex.coerceIn(0, filtered.lastIndex)
        listState.ensureHighlightVisible(idx, defaultRowHeightPx)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) {
                    onCloseRequest()
                    true
                } else {
                    false
                }
            },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionDown -> {
                            if (filtered.isEmpty()) return@onPreviewKeyEvent false
                            selectedResultIndex = when {
                                selectedResultIndex < 0 -> 0
                                selectedResultIndex < filtered.lastIndex -> selectedResultIndex + 1
                                else -> selectedResultIndex
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (filtered.isEmpty()) return@onPreviewKeyEvent false
                            if (selectedResultIndex <= 0) {
                                selectedResultIndex = -1
                            } else {
                                selectedResultIndex--
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            if (filtered.isEmpty()) return@onPreviewKeyEvent false
                            val idx = if (selectedResultIndex >= 0) {
                                selectedResultIndex
                            } else {
                                0
                            }
                            if (idx in filtered.indices) {
                                onPickRequest(filtered[idx].id)
                                onCloseRequest()
                            }
                            true
                        }
                        else -> false
                    }
                },
            label = { Text("搜索（名称、URL、Body、Headers；空格分隔关键词，须全部命中）") },
            singleLine = true,
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
            ),
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        when {
            query.trim().isEmpty() -> {
                Text(
                    "输入关键字后显示匹配结果",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            filtered.isEmpty() -> {
                Text(
                    "无匹配请求",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    itemsIndexed(filtered, key = { _, row -> row.id }) { index, row ->
                        val location = remember(tree, row.collectionId, row.folderId) {
                            requestParentLocationLabel(tree, row.collectionId, row.folderId)
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            GlobalSearchResultRow(
                                method = row.method,
                                name = row.name,
                                location = location,
                                selected = index == selectedResultIndex,
                                onClick = {
                                    onPickRequest(row.id)
                                    onCloseRequest()
                                },
                            )
                            Divider(
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultRow(
    method: String,
    name: String,
    location: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colors.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                method.uppercase(),
                fontSize = 12.sp,
                color = MaterialTheme.colors.primary.copy(alpha = 0.95f),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                name,
                fontSize = 14.sp,
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            location,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 在请求名称、URL、Headers、Body 四处做不区分大小写的子串匹配（模糊）；
 * 多个词以空白分隔，每个词均须在合并文本某处出现（AND）。
 */
private fun matchesGlobalSearchQuery(
    query: String,
    name: String,
    url: String,
    headersText: String,
    body: String,
): Boolean {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return false
    val hay = buildString {
        append(name.lowercase())
        append('\n')
        append(url.lowercase())
        append('\n')
        append(headersText.lowercase())
        append('\n')
        append(body.lowercase())
    }
    return tokens.all { tok -> hay.contains(tok.lowercase()) }
}
