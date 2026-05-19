package http.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import app.EnvVariable
import http.ExchangeFontMetrics

/**
 * 检测文本中是否含未闭合的 `{{prefix`，若存在则返回 prefix 用于变量过滤。
 * 只匹配变量名字符（字母、数字、下划线、点），避免把 URL 路径文字当成变量名。
 * `{{` 后空格或空也算触发（展示全部变量）。
 */
fun detectEnvVarTrigger(text: String): String? {
    val lastOpen = text.lastIndexOf("{{")
    if (lastOpen < 0) return null
    val afterOpen = text.substring(lastOpen + 2)
    if ("}}" in afterOpen) return null
    val trimmed = afterOpen.trimStart()
    // 空格或空 → 展示全部
    if (trimmed.isEmpty()) return ""
    // 取变量名前缀：只允许字母、数字、下划线、点
    val prefix = trimmed.takeWhile { it.isLetterOrDigit() || it == '_' || it == '.' }
    if (prefix.isEmpty()) return null
    return prefix
}

/**
 * 选中变量后插入 `{{varName}}`，只替换 `{{` 之后的有效变量名部分（含前导空格），
 * 保留后面的 URL 路径等已有内容。
 */
fun applyEnvVarSelection(text: String, varName: String): String {
    val lastOpen = text.lastIndexOf("{{")
    if (lastOpen < 0) return text
    val afterOpen = text.substring(lastOpen + 2)
    // 跳过前导空格，然后找出有效变量名长度
    val trimmed = afterOpen.trimStart()
    val prefix = trimmed.takeWhile { it.isLetterOrDigit() || it == '_' || it == '.' }
    val prefixLen = prefix.length
    val whitespaceCount = afterOpen.length - trimmed.length
    val replaceLen = whitespaceCount + prefixLen
    return text.substring(0, lastOpen + 2) + varName + "}}" + afterOpen.substring(replaceLen)
}

/**
 * 环境变量自动补全下拉列表。
 * [filterText] 当前输入的变量名过滤前缀
 * [envVars] 当前可用环境变量列表
 * [yOffset] 弹出列表相对于输入框的垂直偏移量
 */
@Composable
fun EnvVarAutocompletePopup(
    filterText: String,
    envVars: List<EnvVariable>,
    isDarkTheme: Boolean,
    exchangeMetrics: ExchangeFontMetrics,
    yOffset: Dp = 36.dp,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val filtered = remember(filterText, envVars) {
        envVars.filter { it.key.isNotBlank() && it.key.contains(filterText, ignoreCase = true) }
    }
    if (filtered.isEmpty()) return

    val offsetPx = with(LocalDensity.current) { yOffset.roundToPx() }

    Popup(
        offset = IntOffset(x = 0, y = offsetPx),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (isDarkTheme) Color(0xFF1E1E1E) else Color.White,
                    RoundedCornerShape(4.dp),
                )
                .border(
                    1.dp,
                    if (isDarkTheme) Color.Gray else Color.LightGray,
                    RoundedCornerShape(4.dp),
                )
                .widthIn(max = 400.dp)
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            filtered.forEach { v ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = true,
                                color = MaterialTheme.colors.primary,
                            ),
                        ) {
                            onSelect(v.key)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "{{${v.key}}}",
                            style = MaterialTheme.typography.body2.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = if (isDarkTheme) Color(0xFF569CD6) else Color(0xFF0451A5),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = v.value,
                            style = MaterialTheme.typography.body2.copy(
                                fontSize = exchangeMetrics.compact,
                            ),
                            color = if (isDarkTheme) {
                                Color.Gray
                            } else {
                                Color.DarkGray
                            },
                        )
                    }
                }
            }
        }
    }
}
