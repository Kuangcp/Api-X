# 请求面板与响应展示实现

本文介绍 UI 如何与 HTTP 请求交互，包括状态驱动、JSON 高亮等。

## 5.1 状态驱动的 UI 更新

Compose 的核心是**状态驱动**：状态变化自动触发 UI 重绘。

### 基本模式

```kotlin
@Composable
fun RequestPanel() {
    var url by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("GET") }
    var isLoading by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<String?>(null) }
    
    Column {
        // URL 输入
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") }
        )
        
        // 方法选择
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(method)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("GET", "POST", "PUT", "DELETE").forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        method = it
                        expanded = false
                    })
                }
            }
        }
        
        // 发送按钮
        Button(onClick = { 
            isLoading = true
            scope.launch {
                response = sendRequest(method, url)
                isLoading = false
            }
        }) {
            if (isLoading) CircularProgressIndicator() else Text("发送")
        }
        
        // 响应展示
        response?.let { Text(it) }
    }
}
```

> 项目中的请求面板 (`src/main/kotlin/http/RequestPanel.kt`):
```kotlin
@Composable
fun RequestTopBar(
    method: String,
    methodMenuExpanded: Boolean,
    onMethodChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 方法下拉
        Box {
            TextButton(onClick = { methodMenuExpanded = true }) {
                Text(method, color = methodColor(method))
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = methodMenuExpanded, onDismissRequest = { methodMenuExpanded = false }) {
                HttpMethods.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m, color = methodColor(m)) },
                        onClick = { onMethodChange(m); methodMenuExpanded = false }
                    )
                }
            }
        }
        
        // URL 输入（略）
        
        // 发送按钮
        IconButton(onClick = onSend, enabled = !isLoading) {
            if (isLoading) CircularProgressIndicator()
            else Icon(Icons.Filled.Send, "发送请求")
        }
    }
}
```

## 5.2 JSON 语法高亮实现

使用 `AnnotatedString` 实现语法高亮：

### 颜色配置

```kotlin
data class JsonSyntaxPalette(
    val string: Color,   // 字符串值
    val key: Color,     // 对象键
    val number: Color, // 数字
    val keyword: Color, // true/false/null
    val punctuation: Color, // 标点符号
)

fun palette(dark: Boolean) = if (dark) {
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
```

### 高亮逻辑

```kotlin
fun highlightJsonText(text: String, darkTheme: Boolean): AnnotatedString {
    val c = palette(darkTheme)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isWhitespace() -> { append(ch); i++ }
                ch == '"' -> {
                    val end = closingQuoteIndex(text, i)
                    val isKey = isJsonKeyString(text, end)
                    val color = if (isKey) c.key else c.string
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(i, end + 1))
                    }
                    i = end + 1
                }
                ch == '{' || ch == '[' || ch == ':' || ch == ',' -> {
                    withStyle(SpanStyle(color = c.punctuation)) {
                        append(ch)
                    }
                    i++
                }
                ch.isDigit() -> {
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
                else -> { append(ch); i++ }
            }
        }
    }
}
```

> 项目中的完整实现 (`src/main/kotlin/http/JsonSyntaxHighlight.kt`):
```kotlin
private fun highlightJsonText(text: String, darkTheme: Boolean): AnnotatedString {
    val c = palette(darkTheme)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isWhitespace() -> { append(ch); i++ }
                ch == '"' -> {
                    val end = closingQuoteIndex(text, i)
                    val isKey = isJsonKeyString(text, end)
                    val color = if (isKey) c.key else c.string
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(i, end + 1))
                    }
                    i = end + 1
                }
                // ... 更多语法
            }
        }
    }
}
```

### 使用高亮

```kotlin
@Composable
fun ResponseBody(text: String, highlighted: Boolean, isDark: Boolean) {
    val displayText = if (highlighted) {
        formatAndHighlightJsonOrNull(text, isDark) ?: text
    } else {
        AnnotatedString(text)
    }
    
    Text(
        text = displayText,
        modifier = Modifier.padding(8.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
}
```

## 5.3 Form 表单数据处理

### URL 编码表单

```kotlin
fun formUrlEncode(params: Map<String, String>): String {
    return params.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k)}=${URLEncoder.encode(v)}"
    }
}
```

### 解析表单数据

> 项目中的实现 (`src/main/kotlin/http/FormUrlEncodedBody.kt`):
```kotlin
object FormUrlEncodedBody {
    fun parse(text: String): List<Pair<String, String>> {
        return text.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx > 0) {
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                } else null
            }
    }
    
    fun encode(pairs: List<Pair<String, String>>): String {
        return pairs.joinToString("\n") { (k, v) ->
            "$k: $v"
        }
    }
}
```

### 表单与请求体转换

```kotlin
fun migrateFormBodyToEditorLinesIfNeeded(bodyText: String, headersText: String): String {
    val hasFormContentType = headersText.lines()
        .map { it.lowercase() }
        .any { it.contains("application/x-www-form-urlencoded") }
    
    return if (hasFormContentType && bodyText.contains('=')) {
        FormUrlEncodedBody.encode(FormUrlEncodedBody.parse(bodyText))
    } else {
        bodyText
    }
}
```

## 5.4 请求历史存储

### 存储结构

```kotlin
data class HistoryEntry(
    val requestId: String,
    val responseBody: String,
    val statusCode: Int,
    val timestamp: Long,
)
```

### 存储实现

> 项目中的实现 (`src/main/kotlin/db/RequestResponseStore.kt`):
```kotlin
object RequestResponseStore {
    private val historyDir = File(dataHome(), "history")
    
    fun saveLatest(requestId: String, entry: ResponseSnapshot) {
        historyDir.mkdirs()
        val file = latestFile(requestId)
        file.writeText(Json.encodeToString(ResponseSnapshot.serializer(), entry))
    }
    
    fun loadLatest(requestId: String): ResponseSnapshot? {
        val file = latestFile(requestId)
        return if (file.exists()) {
            Json.decodeFromString(ResponseSnapshot.serializer(), file.readText())
        } else null
    }
    
    fun listHistory(requestId: String): List<HistoryEntry> {
        // ...
    }
}
```

> 项目中的调用 (`src/main/kotlin/app/Main.kt:234-236`):
```kotlin
val historyEntries by remember(responseScopeKey) {
    mutableStateOf(editorRequestId?.let { RequestResponseStore.listHistory(it) } ?: emptyList())
}
```

## 5.5 响应状态展示

### 状态码颜色

```kotlin
fun statusCodeColor(code: Int): Color = when {
    code in 200..299 -> Color(0xFF4CAF50)  // 绿色
    code in 300..399 -> Color(0xFFFFC107)  // 黄色
    code in 400..499 -> Color(0xFFFF9800)  // 橙色
    code in 500..599 -> Color(0xFFF44336)  // 红色
    else -> Color.Gray
}
```

### 响应时间格式化

```kotlin
fun formatResponseTime(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60000 -> "${ms / 1000}s"
    else -> "${ms / 60000}分${(ms % 60000) / 1000}秒"
}
```

### 响应大小格式化

```kotlin
fun formatResponseSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
```

## 5.6 Tab 切换

```kotlin
var rightTabIndex by remember { mutableStateOf(0) }

Row {
    Tab(selected = rightTabIndex == 0, onClick = { rightTabIndex = 0 }) {
        Text("响应体")
    }
    Tab(selected = rightTabIndex == 1, onClick = { rightTabIndex = 1 }) {
        Text("请求头")
    }
    Tab(selected = rightTabIndex == 2, onClick = { rightTabIndex = 2 }) {
        Text("响应头")
    }
}
```

## 5.7 总结

| 功能 | 关键实现 |
|------|---------|
| 状态驱动 | `mutableStateOf` + `remember` |
| JSON 高亮 | `AnnotatedString` + `buildAnnotatedString` |
| 表单处理 | `FormUrlEncodedBody.parse/encode` |
| 历史存储 | `RequestResponseStore` |
| Tab 切换 | `TabRow` + 状态索引 |

**下篇**：SQLite 在 Kotlin 中的使用