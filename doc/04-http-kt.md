# Java HttpURLConnection 到 Kotlin 协程

本文介绍 JDK 21 HttpClient API 与 Kotlin 协程的结合使用。

## 4.1 JDK 21 HttpClient API

JDK 11 引入了新的 HttpClient，取代老的 `HttpURLConnection`：

### 基础 GET 请求

```kotlin
val client = HttpClient.newHttpClient()
val request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/data"))
    .GET()
    .build()

val response = client.send(request, HttpResponse.BodyHandlers.ofString())
println(response.body())
```

### POST 请求带 Body

```kotlin
val bodyPublisher = HttpRequest.BodyPublishers.ofString("""{"name":"test"}""")
val request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .header("Content-Type", "application/json")
    .POST(bodyPublisher)
    .build()
```

> 项目中的实际用法 (`src/main/kotlin/http/HttpStreaming.kt:214-263`):
```kotlin
fun sendRequestStreaming(
    method: String,
    url: String,
    body: String,
    headersText: String,
    control: RequestControl,
    connectMs: Long = 10_000L,
    readMs: Long = 6_000L,
    requestTimeoutMs: Long = 10_000L,
    onSseDetected: (Boolean) -> Unit = {},
    onStatusCode: (Int) -> Unit = {},
    onProgress: (Long) -> Unit = {},
    onResponseHeaders: (List<String>) -> Unit = {},
    onChunk: (String) -> Unit = {},
) {
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectMs))
        .proxy(ApiXProxySelector)
        .build()
    
    val builder = HttpRequest.newBuilder()
        .uri(URI.create(url.trim()))
        .timeout(Duration.ofMillis(requestTimeoutMs))
    
    val allowedHeaders = headersAppliedByHttpClient(headersText)
    allowedHeaders.forEach { (name, value) ->
        builder.header(name, value)
    }
    
    val bodyPub = HttpRequest.BodyPublishers.ofString(body)
    val request = when (m) {
        "GET" -> builder.GET().build()
        "POST" -> builder.POST(bodyPub).build()
        "PUT" -> builder.PUT(bodyPub).build()
        "DELETE" -> builder.DELETE().build()
        else -> builder.method(m, bodyPub).build()  // 支持 PATCH, HEAD, OPTIONS 及自定义方法
    }
}
```

## 4.2 协程与 suspend 函数

协程是 Kotlin 的轻量级线程解决方案：

### 基本概念

```kotlin
suspend fun fetchData(): String {
    // 异步操作
    return withContext(Dispatchers.IO) {
        httpClient.send(request, BodyHandlers.ofString()).body()
    }
}
```

### Dispatchers

- `Dispatchers.Default` - CPU 密集型计算
- `Dispatchers.IO` - IO 密集型操作
- `Dispatchers.Main` - UI 线程（Compose 专用）

### 在 Compose 中调用 suspend 函数

```kotlin
@Composable
fun MyScreen() {
    var data by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        data = fetchData()
    }
}
```

## 4.3 异步请求与 Flow

### 使用 Flow 流式处理

对于流式响应（如 SSE），使用 `Flow`：

```kotlin
fun requestBodyFlow(url: String): Flow<String> = flow {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder().uri(URI.create(url)).build()
    
    client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).use { response ->
        response.body().bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                emit(line)
            }
        }
    }
}
```

> 项目中的 SSE 流式处理 (`src/main/kotlin/http/HttpStreaming.kt`):
```kotlin
if (isSse) {
    onChunk("SSE 流式响应中...\n\n")
    var firstSseEventArrived = false
    BufferedReader(InputStreamReader(stream)).use { reader ->
        while (true) {
            if (control.cancelled || Thread.currentThread().isInterrupted) break
            if (requestWallClockExceeded(control, requestTimeoutMs)) {
                control.requestFailed = true
                onChunk("\n[请求总超时]\n")
                break
            }
            val line = reader.readLine() ?: break
            if (!firstSseEventArrived && line.isNotBlank()) {
                firstSseEventArrived = true
            }
            if (firstSseEventArrived) {
                onChunk(line + "\n")
            }
        }
    }
}
```

### 回调式 API 与 Flow

项目中使用回调处理进度（`BufferUpdate` 线程安全地积累行数据，主线程每 100ms 刷新）：

```kotlin
fun sendRequestStreaming(
    // ...
    control: RequestControl,   // 控制取消、超时、BufferUpdate
    onSseDetected: (Boolean) -> Unit,
    onStatusCode: (Int) -> Unit,
    onProgress: (Long) -> Unit,
    onResponseHeaders: (List<String>) -> Unit,
    onChunk: (String) -> Unit,
)
```

调用处 (`src/main/kotlin/app/AppViewModel.kt:546-554`):
```kotlin
sendRequestStreaming(
    method = method, url = effectiveRequestUrl, body = reqBodySnap, headersText = finalHeaders,
    control = control,
    onSseDetected = { isSse -> EventQueue.invokeLater { session.isSseResponse = isSse } },
    onStatusCode = { code -> EventQueue.invokeLater { session.statusCodeText = code.toString() } },
    onProgress = { bytes -> EventQueue.invokeLater { session.responseSizeText = formatBytes(bytes) } },
    onResponseHeaders = { lines -> EventQueue.invokeLater {
        session.responseHeaderLines.clear(); session.responseHeaderLines.addAll(lines)
    } },
    onChunk = { chunk -> control.lineBuffer.append(chunk); control.appendRawResponse(chunk) }
)
```

## 4.4 响应流式处理（Streaming）

### 动态解压

处理 gzip/deflate 压缩的响应体：

```kotlin
private fun wrapResponseBodyStream(
    raw: InputStream,
    headers: HttpHeaders,
    control: RequestControl,
): InputStream {
    val encodings = parseContentEncodingTokens(headers)
    val supported = encodings.filter { it in DECODING_CHAIN }
    
    if (supported.isEmpty()) {
        // 未压缩，尝试检测
        val peek = BufferedInputStream(raw)
        peek.mark(2)
        if (peek.read() == 0x1f && peek.read() == 0x8b) {
            return GZIPInputStream(peek)
        }
        return peek
    }
    
    // 解压
    var stream: InputStream = raw
    for (token in supported.asReversed()) {
        when (token) {
            "gzip", "x-gzip" -> stream = GZIPInputStream(stream)
            "deflate" -> stream = InflaterInputStream(stream, Inflater())
        }
    }
    return stream
}
```

> 项目中的实现 (`src/main/kotlin/http/HttpStreaming.kt`):
```kotlin
private fun wrapResponseBodyStream(
    raw: InputStream,
    headers: HttpHeaders,
    control: RequestControl,
): InputStream {
    control.responseBodyDecodedForHar = false
    val encodings = parseContentEncodingTokens(headers)
    val supported = encodings.filter { it in DECODING_CHAIN }
    if (supported.isEmpty()) {
        if (encodings.isNotEmpty()) {
            return BufferedInputStream(raw)  // 无法解压时透传
        }
        // 尝试自动检测 gzip
        val peek = BufferedInputStream(raw)
        peek.mark(2)
        val b0 = peek.read()
        val b1 = peek.read()
        peek.reset()
        if (b0 == 0x1f && b1 == 0x8b) {
            control.responseBodyDecodedForHar = true
            return GZIPInputStream(peek)
        }
        return peek
    }
    // 按 Content-Encoding 链解压
    control.responseBodyDecodedForHar = true
    var stream: InputStream = raw
    for (token in supported.asReversed()) {
        when (token) {
            "gzip", "x-gzip" -> stream = GZIPInputStream(stream)
            "deflate" -> stream = InflaterInputStream(BufferedInputStream(stream), Inflater())
        }
    }
    return stream
}
```

### 流式读取响应体

```kotlin
val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
val stream = response.body()

BufferedReader(InputStreamReader(stream)).use { reader ->
    val buffer = CharArray(8192)
    while (true) {
        val len = reader.read(buffer) ?: break
        process(buffer, 0, len)
    }
}
```

## 4.5 协程作用域管理

### LaunchedEffect 自动取消

```kotlin
@Composable
fun RequestPanel() {
    var isLoading by remember { mutableStateOf(false) }
    var activeRequestThread by remember { mutableStateOf<Thread?>(null) }
    
    Button(
        onClick = {
            isLoading = true
            activeRequestThread = thread {
                try {
                    sendRequestStreaming(...)
                } finally {
                    isLoading = false
                }
            }
        }
    ) {
        if (isLoading) CircularProgressIndicator()
        else Text("发送")
    }
}
```

### 取消机制

```kotlin
data class RequestControl(
    var cancelled: Boolean = false,
    var requestFailed: Boolean = false,
    val startTimeMs: Long = System.currentTimeMillis(),
    val lineBuffer: BufferUpdate = BufferUpdate(),
    var activeInput: InputStream? = null,
    var responseStatusCode: Int = -1,
    var totalBytes: Long = 0,
    // ...
)

fun sendRequestStreaming(control: RequestControl, ...) {
    try {
        if (control.cancelled) return
        // 处理请求
        while (running) {
            if (control.cancelled) break
        }
    } finally {
        closeQuietly(control.activeInput)  // 确保流关闭
    }
}
```

取消时还通过 `control.activeInput.close()` 中断阻塞的读取操作。
项目中的取消控制 (`src/main/kotlin/app/AppViewModel.kt:591-624`):
```kotlin
fun cancelActiveRequest() {
    val control = session.control ?: return
    control.cancelled = true
    closeQuietly(control.activeInput)  // 关闭输入流，中断阻塞
    control.lineBuffer.append("\n[请求已取消]\n")
    // 保存已获得的响应数据到 HAR
}
```

## 4.6 请求体编码

对于表单类型的请求体，使用 `bodyWirePayloadForHttp` 将 Editor 中的 `Key: Value` 行格式编码为 HTTP wire 格式：

```kotlin
fun bodyWirePayloadForHttp(bodyText: String, headersText: String): String {
    return if (hasFormContentType(headersText) && bodyText.contains(':')) {
        // 将 "key: value" 行转换为 "key=value&key2=value2"
        FormUrlEncodedBody.encode(FormUrlEncodedBody.parse(bodyText))
    } else {
        bodyText  // 其他类型直接透传
    }
}
```

## 4.7 超时体系

| 超时类型 | 默认值 | 说明 |
|---------|--------|------|
| `connectTimeout` | 10 秒 | 建立 TCP 连接的超时 |
| `readTimeout` | 6 秒 | 读取单个块的超时 |
| `requestTimeout` | 10 秒 | 整次请求总超时（含读取所有正文） |

三个超时在 `AppSettings` 中独立配置，支持用户在设置面板中调整。

## 4.8 总结

| 特性 | 说明 |
|------|------|
| `HttpClient` | JDK 21 内置，替代 HttpURLConnection |
| `suspend` | 挂起函数，非阻塞等待 |
| `Dispatchers.IO` | IO 密集型操作专用线程池 |
| `Flow` | 响应式流，处理流式数据 |
| `BodyHandlers.ofInputStream` | 流式处理大响应 |
| `BufferUpdate` | 线程安全的行缓冲，定刷新放 |
| `bodyWirePayloadForHttp` | Form/JSON 自动编码 |

**下篇**：请求面板与响应展示实现