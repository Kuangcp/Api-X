package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import db.CollectionRepository
import http.AuthEditor
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
                when (section) {
                    0 -> {
                        AuthEditor(
                            auth = authState,
                            onAuthChange = { authState = it },
                            exchangeMetrics = exchangeMetrics,
                            modifier = Modifier.fillMaxSize()
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
                        is TreeSelection.Collection -> repository.updateCollectionAuth(target.id, authState)
                        is TreeSelection.Folder -> repository.updateFolderAuth(target.id, authState)
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
