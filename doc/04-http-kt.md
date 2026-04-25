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
    // ...
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
        // ...
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

> 项目中的 SSE 流式处理 (`src/main/kotlin/http/HttpStreaming.kt:286-320`):
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

项目中使用回调处理进度：

```kotlin
fun sendRequestStreaming(
    // ...
    onSseDetected: (Boolean) -> Unit,
    onStatusCode: (Int) -> Unit,
    onResponseTime: (Long) -> Unit,
    onProgress: (Long) -> Unit,
    onResponseHeaders: (List<String>) -> Unit,
    onChunk: (String) -> Unit
)
```

调用处 (`src/main/kotlin/app/Main.kt`):
```kotlin
sendRequestStreaming(
    method = method,
    url = fullUrl,
    body = bodyText,
    headersText = headersText,
    control = requestControl,
    onSseDetected = { isSseResponse = it },
    onStatusCode = { statusCodeText = it.toString() },
    onResponseTime = { responseTimeText = "${it}ms" },
    onProgress = { /* 进度 */ },
    onResponseHeaders = { responseHeaderLines.clear(); responseHeaderLines.addAll(it) },
    onChunk = { responseLines.add(it) }
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

> 项目中的实现 (`src/main/kotlin/http/HttpStreaming.kt:149-197`):
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
            return BufferedInputStream(raw)
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
    val startTimeMs: Long = System.currentTimeMillis(),
    // ...
)

fun sendRequestStreaming(control: RequestControl, ...) {
    while (running) {
        if (control.cancelled) break
        // 处理请求
    }
}
```

项目中的取消控制 (`src/main/kotlin/http/HttpStreaming.kt:227-228`):
```kotlin
fun sendRequestStreaming(..., control: RequestControl, ...) {
    try {
        if (control.cancelled) return
        // ...
    }
}
```

## 4.6 总结

| 特性 | 说明 |
|------|------|
| `HttpClient` | JDK 21 内置，替代 HttpURLConnection |
| `suspend` | 挂起函数，非阻塞等待 |
| `Dispatchers.IO` | IO 密集型操作专用线程池 |
| `Flow` | 响应式流，处理流式数据 |
| `BodyHandlers.ofInputStream` | 流式处理大响应 |

**下篇**：请求面板与响应展示实现