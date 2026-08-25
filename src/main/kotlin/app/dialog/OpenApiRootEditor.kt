package app.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import openapi.OpenApiRoot

@Composable
fun OpenApiRootEditor(
    root: OpenApiRoot?,
    envKeys: List<String>,
    onRootChange: (OpenApiRoot?) -> Unit,
) {
    val mode = root?.mode ?: OpenApiRoot.MODE_BASE_URL
    val envKey = root?.envKey.orEmpty()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == OpenApiRoot.MODE_BASE_URL,
                onClick = { onRootChange(OpenApiRoot(OpenApiRoot.MODE_BASE_URL)) },
            )
            Text("使用主域名（OpenAPI servers / 源地址）", color = MaterialTheme.colors.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == OpenApiRoot.MODE_ENV,
                onClick = { onRootChange(OpenApiRoot(OpenApiRoot.MODE_ENV, envKey.ifBlank { null })) },
            )
            Text("使用环境变量", color = MaterialTheme.colors.onSurface)
        }
        if (mode == OpenApiRoot.MODE_ENV) {
            Spacer(Modifier.height(8.dp))
            EnvKeyField(
                value = envKey,
                envKeys = envKeys,
                onValueChange = { value -> onRootChange(OpenApiRoot(OpenApiRoot.MODE_ENV, value.trim().ifBlank { null })) },
            )
            Text(
                "请求 URL 将生成为 {{key}}/path，例如 {{host}}/v1/xxx。",
                color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EnvKeyField(
    value: String,
    envKeys: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("变量 key") },
            placeholder = { Text("host") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择环境变量")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (envKeys.isEmpty()) {
                DropdownMenuItem(onClick = {}, enabled = false) {
                    Text("无可用环境变量")
                }
            } else {
                envKeys.forEach { key ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onValueChange(key)
                    }) {
                        Text(key)
                    }
                }
            }
        }
    }
}
