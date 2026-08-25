package app.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import app.ui.apiXDarkColors
import app.ui.parseHexColorOrNull
import db.CollectionRepository
import http.request.AuthEditor
import http.ExchangeFontMetrics
import tree.PostmanAuth
import tree.TreeSelection

@Composable
fun CollectionSettingsDialog(
    visible: Boolean,
    target: TreeSelection?,
    repository: CollectionRepository,
    isDarkTheme: Boolean,
    typographyBase: Typography,
    exchangeMetrics: ExchangeFontMetrics,
    envKeys: List<String> = emptyList(),
    onCloseRequest: () -> Unit,
) {
    if (!visible || target == null) return

    val title = when (target) {
        is TreeSelection.Collection -> "集合设置"
        is TreeSelection.Folder -> "文件夹设置"
        else -> "设置"
    }

    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = title,
        state = rememberDialogState(width = 700.dp, height = 500.dp),
    ) {
        MaterialTheme(
            colors = if (isDarkTheme) apiXDarkColors() else lightColors(background = Color(0xFFF2F2F2)),
            typography = typographyBase,
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                CollectionSettingsBody(
                    target = target,
                    repository = repository,
                    exchangeMetrics = exchangeMetrics,
                    isDarkTheme = isDarkTheme,
                    envKeys = envKeys,
                    onCancel = onCloseRequest,
                    onSave = {
                        onCloseRequest()
                    }
                )
            }
        }
    }
}

@Composable
private fun CollectionSettingsBody(
    target: TreeSelection,
    repository: CollectionRepository,
    exchangeMetrics: ExchangeFontMetrics,
    isDarkTheme: Boolean,
    envKeys: List<String>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var section by remember { mutableIntStateOf(0) }
    var authState by remember {
        mutableStateOf<PostmanAuth?>(
            when (target) {
                is TreeSelection.Collection -> repository.getCollectionAuth(target.id)
                is TreeSelection.Folder -> repository.getFolderAuth(target.id)
                else -> null
            }
        )
    }
    var openApiSourceState by remember(target) {
        mutableStateOf(
            when (target) {
                is TreeSelection.Collection -> repository.getCollectionOpenApiSource(target.id).orEmpty()
                else -> ""
            }
        )
    }
    var openApiRootState by remember(target) {
        mutableStateOf(
            when (target) {
                is TreeSelection.Collection -> repository.getCollectionOpenApiRoot(target.id)
                else -> null
            }
        )
    }
    val initialColor = remember(target) {
        when (target) {
            is TreeSelection.Collection -> repository.getCollectionColor(target.id)
            is TreeSelection.Folder -> repository.getFolderColor(target.id)
            else -> null
        }
    }
    var colorHex by remember(target) { mutableStateOf(initialColor) }

    val isCollection = target is TreeSelection.Collection
    val colorSection = if (isCollection) 2 else 1

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left Sidebar
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 8.dp),
            ) {
                NavRow(
                    label = "认证 (Auth)",
                    selected = section == 0,
                    onClick = { section = 0 },
                )
                if (isCollection) {
                    NavRow(
                        label = "OpenAPI",
                        selected = section == 1,
                        onClick = { section = 1 },
                    )
                }
                NavRow(
                    label = "颜色",
                    selected = section == colorSection,
                    onClick = { section = colorSection },
                )
            }

            Divider(
                modifier = Modifier.width(1.dp).fillMaxHeight(),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            )

            // Right Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
            ) {
                when {
                    section == 0 -> {
                        AuthEditor(
                            auth = authState,
                            onAuthChange = { authState = it },
                            exchangeMetrics = exchangeMetrics,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    section == 1 && isCollection -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("OpenAPI source URL", color = MaterialTheme.colors.onSurface)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = openApiSourceState,
                                onValueChange = { openApiSourceState = it },
                                label = { Text("/v3/api-docs URL") },
                                placeholder = { Text("http://localhost:8080/v3/api-docs") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Right-click this collection to refresh from the bound OpenAPI URL.",
                                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                style = MaterialTheme.typography.caption,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("请求 URL 的 root 来源", color = MaterialTheme.colors.onSurface)
                            Spacer(Modifier.height(8.dp))
                            OpenApiRootEditor(
                                root = openApiRootState,
                                envKeys = envKeys,
                                onRootChange = { openApiRootState = it },
                            )
                        }
                    }
                    section == colorSection -> {
                        ColorSettingsContent(
                            colorHex = colorHex,
                            onColorChange = { colorHex = it },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            Button(
                onClick = {
                    when (target) {
                        is TreeSelection.Collection -> {
                            repository.updateCollectionAuth(target.id, authState)
                            repository.updateCollectionOpenApiSource(target.id, openApiSourceState.trim().takeIf { it.isNotBlank() })
                            repository.updateCollectionOpenApiRoot(target.id, openApiRootState)
                            repository.updateCollectionColor(target.id, resolveColorValue(colorHex, initialColor))
                        }
                        is TreeSelection.Folder -> {
                            repository.updateFolderAuth(target.id, authState)
                            repository.updateFolderColor(target.id, resolveColorValue(colorHex, initialColor))
                        }
                        else -> {}
                    }
                    onSave()
                },
            ) {
                Text("保存")
            }
        }
    }
}

private val presetColorHexes = listOf(
    "#E53935",
    "#FB8C00",
    "#FDD835",
    "#43A047",
    "#00897B",
    "#1E88E5",
    "#3949AB",
    "#8E24AA",
    "#D81B60",
    "#6D4C41",
)

private val hexColorPattern = Regex("^#?[0-9a-fA-F]{6}$")

private fun resolveColorValue(input: String?, fallback: String?): String? {
    val t = input?.trim().orEmpty()
    if (t.isEmpty()) return null
    if (!hexColorPattern.matches(t)) return fallback
    return if (t.startsWith("#")) t else "#$t"
}

@Composable
private fun ColorSettingsContent(
    colorHex: String?,
    onColorChange: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("名称与图标颜色", color = MaterialTheme.colors.onSurface)
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presetColorHexes.forEach { hex ->
                val selected = colorHex?.equals(hex, ignoreCase = true) == true
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(parseHexColorOrNull(hex) ?: Color.Transparent)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.25f),
                            shape = CircleShape,
                        )
                        .clickable { onColorChange(hex) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "自定义颜色（#RRGGBB）",
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            style = MaterialTheme.typography.caption,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = colorHex.orEmpty(),
            onValueChange = { onColorChange(it.ifBlank { null }) },
            label = { Text("颜色值") },
            placeholder = { Text("#1E88E5") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 220.dp),
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { onColorChange(null) }) {
            Text("跟随主题")
        }
        Text(
            "跟随主题时，图标与名称使用当前日/夜主题的默认配色；手动设置后不再随主题切换。",
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
            style = MaterialTheme.typography.caption,
        )
    }
}

@Composable
private fun NavRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colors.primary.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colors.onSurface
            } else {
                MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            },
            style = MaterialTheme.typography.body2,
        )
    }
}
