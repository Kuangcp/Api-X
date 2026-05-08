package http

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val jsonLenient = Json { ignoreUnknownKeys = true }
private val jsonPretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
}

/**
 * 将正文解析为 JSON 并 pretty 打印；空白或解析失败时返回 null（与 [formatAndHighlightJsonOrNull] 使用相同规则）。
 */
fun formatJsonBodyTextOrNull(rawBody: String): String? {
    val trimmed = rawBody.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        val el = jsonLenient.parseToJsonElement(trimmed)
        jsonPretty.encodeToString(JsonElement.serializer(), el)
    }.getOrNull()
}

fun contentTypeHeaderIndicatesJson(headerLines: List<String>): Boolean {
    for (line in headerLines) {
        val p = parseHeaderLine(line) ?: continue
        if (!p.first.equals("Content-Type", ignoreCase = true)) continue
        val v = p.second.lowercase()
        if (v.contains("application/json")) return true
        if (v.contains("+json")) return true
    }
    return false
}

/**
 * 将正文格式化为 pretty JSON 并生成带语法色的 [AnnotatedString]；解析失败返回 null。
 */
fun formatAndHighlightJsonOrNull(rawBody: String, darkTheme: Boolean): AnnotatedString? {
    val pretty = formatJsonBodyTextOrNull(rawBody) ?: return null
    return highlightJsonText(pretty, darkTheme)
}

private data class JsonSyntaxPalette(
    val string: Color,
    val key: Color,
    val number: Color,
    val keyword: Color,
    val punctuation: Color,
)

private fun palette(dark: Boolean) = if (dark) {
    JsonSyntaxPalette(
        string = Color(0xFFCE9178),
        key = Color(0xFF9CDCFE),
        number = Color(0xFFB5CEA8),
        keyword = Color(0xFF569CD6),
        punctuation = Color(0xFFD4D4D4),
    )
} else {
    JsonSyntaxPalette(
        string = Color(0xFFA31515),
        key = Color(0xFF0451A5),
        number = Color(0xFF098658),
        keyword = Color(0xFF0000FF),
        punctuation = Color(0xFF242424),
    )
}

private fun highlightJsonText(text: String, darkTheme: Boolean): AnnotatedString {
    val c = palette(darkTheme)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isWhitespace() -> {
                    append(ch)
                    i++
                }
                ch == '"' -> {
                    val end = closingQuoteIndex(text, i)
                    val isKey = isJsonKeyString(text, end)
                    val color = if (isKey) c.key else c.string
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(i, end + 1))
                    }
                    i = end + 1
                }
                ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ':' || ch == ',' -> {
                    withStyle(SpanStyle(color = c.punctuation)) {
                        append(ch)
                    }
                    i++
                }
                ch == '-' || ch.isDigit() -> {
                    val end = numberEndIndex(text, i)
                    withStyle(SpanStyle(color = c.number)) {
                        append(text.substring(i, end + 1))
                    }
                    i = end + 1
                }
                text.startsWith("true", i) -> {
                    withStyle(SpanStyle(color = c.keyword)) { append("true") }
                    i += 4
                }
                text.startsWith("false", i) -> {
                    withStyle(SpanStyle(color = c.keyword)) { append("false") }
                    i += 5
                }
                text.startsWith("null", i) -> {
                    withStyle(SpanStyle(color = c.keyword)) { append("null") }
                    i += 4
                }
                else -> {
                    append(ch)
                    i++
                }
            }
        }
    }
}

/** `start` 为开引号 `"` 的下标，返回闭引号下标。 */
private fun closingQuoteIndex(text: String, start: Int): Int {
    var j = start + 1
    while (j < text.length) {
        when (text[j]) {
            '\\' -> j += 2
            '"' -> return j
            else -> j++
        }
    }
    return text.length - 1
}

/** 闭引号之后（跳过空白）是否为 `:`，用于区分 key 与普通字符串。 */
private fun isJsonKeyString(text: String, closingQuoteIndex: Int): Boolean {
    var j = closingQuoteIndex + 1
    while (j < text.length && text[j].isWhitespace()) j++
    return j < text.length && text[j] == ':'
}

private fun numberEndIndex(text: String, start: Int): Int {
    var j = start
    if (j < text.length && text[j] == '-') j++
    while (j < text.length && text[j].isDigit()) j++
    if (j < text.length && text[j] == '.') {
        j++
        while (j < text.length && text[j].isDigit()) j++
    }
    if (j < text.length && (text[j] == 'e' || text[j] == 'E')) {
        j++
        if (j < text.length && (text[j] == '+' || text[j] == '-')) j++
        while (j < text.length && text[j].isDigit()) j++
    }
    return (j - 1).coerceAtLeast(start)
}
