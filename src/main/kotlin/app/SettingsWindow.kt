package app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

@Composable
fun SettingsDialogWindow(
    visible: Boolean,
    isDarkTheme: Boolean,
    typographyBase: Typography,
    onCloseRequest: () -> Unit,
    onSaved: (AppSettings) -> Unit,
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "设置",
        state = rememberDialogState(width = 840.dp, height = 680.dp),
    ) {
        MaterialTheme(
            colors = if (isDarkTheme) {
                apiXDarkColors()
            } else {
                lightColors(background = hexToColor("#f2f2f2"))
            },
            typography = typographyBase,
        ) {
            SettingsDialogBody(
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
private fun SettingsDialogBody(
    onCancel: () -> Unit,
    onSave: (AppSettings) -> Unit,
) {
    var section by remember { mutableIntStateOf(0) }
    var fontFamily by remember { mutableStateOf(AppSettingsStore.snapshot().fontFamilyName) }
    var fontSize by remember { mutableFloatStateOf(AppSettingsStore.snapshot().fontSizeSp) }
    var requestResponseFontSize by remember {
        mutableFloatStateOf(AppSettingsStore.snapshot().requestResponseFontSizeSp)
    }
    var backgroundHex by remember { mutableStateOf(AppSettingsStore.snapshot().backgroundHex) }
    var httpProxy by remember { mutableStateOf(AppSettingsStore.snapshot().httpProxyUrl) }
    var httpsProxy by remember { mutableStateOf(AppSettingsStore.snapshot().httpsProxyUrl) }
    var bypassText by remember { mutableStateOf(AppSettingsStore.snapshot().bypassRegexLines) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .width(168.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = 8.dp),
            ) {
                NavRow(
                    label = "通用",
                    selected = section == 0,
                    onClick = { section = 0; errorText = null },
                )
                NavRow(
                    label = "代理",
                    selected = section == 1,
                    onClick = { section = 1; errorText = null },
                )
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
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (section) {
                    0 -> {
                        Text(
                            "界面字体",
                            style = MaterialTheme.typography.subtitle2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        )
                        OutlinedTextField(
                            value = fontFamily,
                            onValueChange = { fontFamily = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("字体名称") },
                            placeholder = { Text("留空为系统默认，如 Segoe UI、Microsoft YaHei UI") },
                            singleLine = true,
                        )
                        Text(
                            "字体大小（sp）: ${"%.0f".format(fontSize)}",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface,
                        )
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 8f..24f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "请求 / 响应区字体（sp）: ${"%.0f".format(requestResponseFontSize)}",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface,
                        )
                        Text(
                            "中间 Request、Response 正文与相关控件；与上方全局界面字体无关。",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        )
                        Slider(
                            value = requestResponseFontSize,
                            onValueChange = { requestResponseFontSize = it },
                            valueRange = 9f..28f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "背景色",
                            style = MaterialTheme.typography.subtitle2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(onClick = { backgroundHex = "" }) {
                                Text("默认")
                            }
                            for ((label, hex) in BACKGROUND_COLOR_PRESETS) {
                                TextButton(onClick = { backgroundHex = hex }) {
                                    Text(label)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val preview = parseHexColorOrNull(backgroundHex)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .background(
                                        preview ?: MaterialTheme.colors.surface,
                                        RoundedCornerShape(6.dp),
                                    ),
                            )
                            OutlinedTextField(
                                value = backgroundHex,
                                onValueChange = { backgroundHex = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("HEX") },
                                placeholder = { Text("#282923 或留空用主题默认") },
                                singleLine = true,
                            )
                        }
                    }
                    else -> {
                        Text(
                            "网络代理",
                            style = MaterialTheme.typography.subtitle2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        )
                        OutlinedTextField(
                            value = httpProxy,
                            onValueChange = { httpProxy = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("HTTP 代理") },
                            placeholder = { Text("例如 http://127.0.0.1:7890 或 127.0.0.1:7890") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = httpsProxy,
                            onValueChange = { httpsProxy = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("HTTPS 代理") },
                            placeholder = { Text("可与 HTTP 不同；留空则回退到 HTTP 代理") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = bypassText,
                            onValueChange = { bypassText = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            label = { Text("直连白名单（正则）") },
                            placeholder = {
                                Text("每行一条正则，匹配请求主机名时直连、不走代理\n例：.*\\.local\nexample\\.com")
                            },
                            maxLines = 8,
                        )
                    }
                }
                errorText?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.body2,
                    )
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
                    val err = validateBypassLines(bypassText)
                        ?: validateBackgroundHexField(backgroundHex)
                    if (err != null) {
                        errorText = err
                        return@TextButton
                    }
                    errorText = null
                    onSave(
                        AppSettings(
                            fontFamilyName = fontFamily.trim(),
                            fontSizeSp = fontSize.coerceIn(8f, 32f),
                            requestResponseFontSizeSp = requestResponseFontSize.coerceIn(9f, 28f),
                            backgroundHex = backgroundHex.trim(),
                            httpProxyUrl = httpProxy.trim(),
                            httpsProxyUrl = httpsProxy.trim(),
                            bypassRegexLines = bypassText.trimEnd(),
                        ),
                    )
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

private val BACKGROUND_COLOR_PRESETS = listOf(
    "Dracula" to "#282a36",
    "Monokai" to "#272822",
    "One Dark" to "#282c34",
    "Nord" to "#2e3440",
    "Solarized Dark" to "#002b36",
    "Gruvbox" to "#282828",
)

private fun validateBypassLines(text: String): String? {
    for (line in text.lines()) {
        val t = line.trim()
        if (t.isEmpty()) continue
        runCatching { Regex(t) }.exceptionOrNull()?.let { e ->
            return "白名单正则无效：「$t」 ${e.message ?: ""}"
        }
    }
    return null
}
