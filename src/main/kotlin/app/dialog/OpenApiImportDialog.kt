package app.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import app.ui.apiXDarkColors

@Composable
fun OpenApiImportDialog(
    visible: Boolean,
    isDarkTheme: Boolean,
    typographyBase: androidx.compose.material.Typography,
    onCloseRequest: () -> Unit,
    onCreate: (name: String, openApiUrl: String, onResult: (Boolean, String?) -> Unit) -> Unit,
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "新建集合",
        state = rememberDialogState(width = 560.dp, height = 380.dp),
    ) {
        MaterialTheme(
            colors = if (isDarkTheme) apiXDarkColors() else lightColors(background = Color(0xFFF2F2F2)),
            typography = typographyBase,
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                var name by remember { mutableStateOf("新集合") }
                var openApiUrl by remember { mutableStateOf("") }
                var isCreating by remember { mutableStateOf(false) }
                var errorText by remember { mutableStateOf<String?>(null) }
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Text("新建集合", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorText = null },
                        label = { Text("集合名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = openApiUrl,
                        onValueChange = { openApiUrl = it; errorText = null },
                        label = { Text("OpenAPI 地址（可选）") },
                        placeholder = { Text("localhost:8080/v3/api-docs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "不填写 OpenAPI 地址时，会创建一个普通空集合；填写后会自动拉取并生成接口目录。未填写协议时默认使用 http://。",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    errorText?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCloseRequest, enabled = !isCreating) {
                            Text("取消")
                        }
                        Button(
                            enabled = name.trim().isNotEmpty() && !isCreating,
                            onClick = {
                                val cleanUrl = openApiUrl.trim()
                                errorText = null
                                if (cleanUrl.isNotEmpty()) isCreating = true
                                onCreate(name.trim(), cleanUrl) { success, message ->
                                    isCreating = false
                                    if (!success) {
                                        errorText = message ?: "OpenAPI 导入失败"
                                    }
                                }
                            },
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("创建")
                            }
                        }
                    }
                }
            }
        }
    }
}