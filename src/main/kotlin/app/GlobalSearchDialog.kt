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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val focusRequester = remember { FocusRequester() }

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
            else rows.filter { matchesGlobalSearchQuery(q, it.name, it.bodyText) }
        }
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
                .focusRequester(focusRequester),
            label = { Text("搜索（请求名称与 Body，空格分隔多个关键词）") },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = MaterialTheme.colors.onSurface,
                cursorColor = MaterialTheme.colors.primary,
                focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
                placeholderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                focusedLabelColor = MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high),
                unfocusedLabelColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    items(filtered, key = { it.id }) { row ->
                        GlobalSearchResultRow(
                            method = row.method,
                            name = row.name,
                            location = requestParentLocationLabel(tree, row.collectionId, row.folderId),
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

@Composable
private fun GlobalSearchResultRow(
    method: String,
    name: String,
    location: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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

private fun matchesGlobalSearchQuery(query: String, name: String, body: String): Boolean {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return false
    val hayName = name.lowercase()
    val hayBody = body.lowercase()
    return tokens.all { tok ->
        val t = tok.lowercase()
        hayName.contains(t) || hayBody.contains(t)
    }
}
