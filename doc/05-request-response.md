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

> 项目中的请求面板分为多个组件：
> - 顶部栏：`src/main/kotlin/http/request/RequestTopBar.kt`
> - 左侧编辑区：`src/main/kotlin/http/request/RequestSidePanel.kt` + `RequestEditorPane.kt`
> - 各标签页：`RequestBodyEditorTab.kt` / `RequestKeyValueTabs.kt` / `AuthEditor.kt`

```kotlin
@Composable
fun RequestTopBar(
    method: String,
    methodMenuExpanded: Boolean,
    onMethodChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    // ... 主题、导入导出等
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
        // URL 输入 + 发送/取消按钮
        // ...
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

响应面板中的 Body 标签支持 JSON 语法高亮切换开关：

```kotlin
@Composable
fun ResponseBody(
    text: String,
    highlighted: Boolean,
    isDark: Boolean,
    exchangeMetrics: ExchangeFontMetrics,
) {
    val displayText = if (highlighted) {
        formatAndHighlightJsonOrNull(text, isDark) ?: text
    } else {
        AnnotatedString(text)
    }
    
    Text(
        text = displayText,
        modifier = Modifier.padding(8.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = exchangeMetrics.body,
    )
}
```

JSON 编辑器（左侧 Body 标签页）另有 `neoutils/highlight-compose` 库提供实时 JSON 语法高亮（`com.neoutils.highlight:highlight-compose:2.3.0`）。

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
        // 将 "key=value" 格式转为 Editor 中 "key: value" 格式
        FormUrlEncodedBody.encode(FormUrlEncodedBody.parse(bodyText))
    } else {
        bodyText
    }
}
```

发送时反向转换通过 `bodyWirePayloadForHttp` 将 Editor 中的 `key: value` 行格式编码为 wire 格式的 `key=value&...`。

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

> 项目中的实现 (`src/main/kotlin/db/RequestResponseStore.kt`): 使用 HAR 1.2 格式存储，每个请求最多保留 10 条历史。

```kotlin
object RequestResponseStore {
    fun save(requestId: String, snapshot: HarSnapshot) {
        // 写入 files/{requestId}/{epochMs}.json 的 HAR 格式文件
    }
    fun loadLatest(requestId: String): HarSnapshot?
    fun listHistory(requestId: String): List<HistoryEntry>
    fun clearResponseAndBenchLogs(requestId: String)
}
```

> 项目中的调用 (`src/main/kotlin/app/AppViewModel.kt:562-575`): 每次请求完成后自动保存 HAR 快照，包含完整的请求/响应信息。

```kotlin
RequestResponseStore.save(boundRequestId, HarSnapshot(
    savedAtEpochMs = System.currentTimeMillis(),
    requestMethod = reqMethodSnap, requestUrl = effectiveRequestUrl,
    requestHeadersFullText = reqHeadersFullSnap, requestBody = reqBodySnap,
    responseStatus = code, responseStatusText = HarLogCodec.responseStatusPhrase(code),
    responseHeaderLines = responseHeaderLinesForHar(...),
    responseBodyLines = control.snapshotRawBodyLines(),
    responseTimeMs = elapsed, responseSizeBytes = control.totalBytes,
    rightTabIndex = tabAtStart, isSseResponse = control.responseWasSse,
))
```

### 会话隔离

每个请求拥有独立的 `RequestSession`，其中包含响应的行、头、状态、LazyListState 等。切换标签时不丢失已收到的响应数据：

```kotlin
data class RequestSession(
    val responseLines: MutableList<String> = mutableListOf(),
    val responseHeaderLines: MutableList<String> = mutableListOf(),
    val responseListState: LazyListState = LazyListState(),
    var isLoading: Boolean = false,
    var isSseResponse: Boolean = false,
    var statusCodeText: String = "",
    var responseTimeText: String = "",
    // ...
)
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
fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60000 -> "${ms / 1000}s"
    else -> "${ms / 60000}分${(ms % 60000) / 1000}秒"
}
```

### 响应大小格式化

```kotlin
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
```

## 5.6 编辑区 Tab 切换

左侧编辑区 4 个标签页（Body / Headers / Params / Auth），通过 `leftTabIndex` 控制：

```kotlin
val tabs = listOf("Body", "Headers", "Params", "Auth")
tabs.forEachIndexed { index, title ->
    Box(modifier = Modifier.clickable { onLeftTabIndexChange(index) }) {
        Text(title)
    }
}
```

右侧响应面板 3 个标签页：
- **Body** - 响应体（支持 JSON 高亮切换）
- **Headers** - 解析后的响应头
- **Request** - 实际发出的请求（含 URL、头、主体）

## 5.7 并发请求

每个请求标签独立管理 `RequestSession`，多个请求可同时发送。树形侧边栏中正在运行的请求会显示加载指示器：

```kotlin
val runningRequestIds = remember { mutableSetOf<String>() }
// 请求开始时：runningRequestIds.add(boundRequestId)
// 请求完成时：runningRequestIds.remove(boundRequestId)
```

## 5.8 Auth 编辑器

每个请求支持四种认证方式：

- **No Auth** - 无认证
- **Inherit** - 从父级文件夹/集合继承
- **Basic Auth** - 用户名/密码，通过 Base64 编码
- **Bearer Token** - 在 Authorization 头中添加 Token
- **API Key** - 自定义 Key-Value（支持 header 或 query 参数）

Auth 解析逻辑在 `http/AuthResolver.kt`，继承链解析在 `CollectionRepository.resolveEffectiveAuth()`。

## 5.9 总结

| 功能 | 关键实现 |
|------|---------|
| 状态驱动 | `AppViewModel` + `RequestSession` |
| JSON 高亮 | `AnnotatedString` + `buildAnnotatedString` |
| 表单处理 | `FormUrlEncodedBody.parse/encode` |
| 历史存储 | HAR 1.2 格式，`RequestResponseStore` |
| Tab 切换 | leftTabIndex + rightTabIndex |
| 并发请求 | 独立 `RequestSession` 隔离状态 |
| Auth 系统 | `AuthResolver` + `AuthEditor` |
| 请求体编码 | `bodyWirePayloadForHttp` |

**下篇**：SQLite 在 Kotlin 中的使用