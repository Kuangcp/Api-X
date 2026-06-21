package app.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
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
    onCreate: (name: String, openApiUrl: String) -> Unit,
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
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Text("新建集合", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("集合名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = openApiUrl,
                        onValueChange = { openApiUrl = it },
                        label = { Text("OpenAPI 地址（可选）") },
                        placeholder = { Text("http://localhost:8080/v3/api-docs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "不填写 OpenAPI 地址时，会创建一个普通空集合。填写后会自动拉取并生成接口目录。",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCloseRequest) {
                            Text("取消")
                        }
                        Button(
                            enabled = name.trim().isNotEmpty(),
                            onClick = {
                                onCreate(name.trim(), openApiUrl.trim())
                            },
                        ) {
                            Text("创建")
                        }
                    }
                }
            }
        }
    }
}
