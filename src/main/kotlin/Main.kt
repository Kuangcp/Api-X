import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.concurrent.thread

@Composable
@Preview
fun App() {
    var method by remember { mutableStateOf("GET") }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("https://httpbin.org/get") }
    var headersText by remember {
        mutableStateOf(
            "Content-Type: application/json\nAccept: application/json"
        )
    }
    var bodyText by remember { mutableStateOf("{\n  \"name\": \"api-x\"\n}") }
    var responseLines by remember { mutableStateOf(mutableStateListOf("响应结果会显示在这里")) }
    var responsePartialLine by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSseResponse by remember { mutableStateOf(false) }
    var activeRequestControl by remember { mutableStateOf<RequestControl?>(null) }
    var activeRequestThread by remember { mutableStateOf<Thread?>(null) }
    val responseListState = rememberLazyListState()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(hexToColor("#f2f2f2"))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        setSingleResponseMessage(responseLines, "设置功能待实现")
                        responsePartialLine = null
                    },
                    enabled = !isLoading
                ) {
                    Text("设置")
                }
                Button(
                    onClick = {
                        try {
                            val clipboardText = readClipboardText()
                            val parsed = parseCurlCommand(clipboardText)
                            method = parsed.method
                            url = parsed.url.ifBlank { url }
                            headersText = parsed.headers.joinToString("\n")
                            bodyText = parsed.body
                            setSingleResponseMessage(responseLines, "已从剪贴板导入 cURL")
                            responsePartialLine = null
                        } catch (e: Exception) {
                            setSingleResponseMessage(responseLines, "导入 cURL 失败: ${e.message}")
                            responsePartialLine = null
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("导入 cURL")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Button(
                        onClick = { methodMenuExpanded = true },
                        enabled = !isLoading
                    ) {
                        Text(method)
                    }
                    DropdownMenu(
                        expanded = methodMenuExpanded,
                        onDismissRequest = { methodMenuExpanded = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            method = "GET"
                            methodMenuExpanded = false
                        }) {
                            Text("GET")
                        }
                        DropdownMenuItem(onClick = {
                            method = "POST"
                            methodMenuExpanded = false
                        }) {
                            Text("POST")
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("输入 URL") },
                    enabled = !isLoading
                )
                Button(
                    onClick = {
                        if (!isLoading) {
                            val control = RequestControl()
                            activeRequestControl = control
                            isLoading = true
                            responseLines = mutableStateListOf()
                            responsePartialLine = null
                            control.lineBuffer.append("请求中...\n")
                            applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) {
                                responsePartialLine = it
                            }
                            val flusher = thread(isDaemon = true) {
                                while (!control.finished && !control.cancelled) {
                                    try {
                                        Thread.sleep(UI_REFRESH_INTERVAL_MS)
                                    } catch (_: InterruptedException) {
                                        break
                                    }
                                    if (activeRequestControl !== control) break
                                    val update = control.lineBuffer.drainUpdate()
                                    if (!update.hasChanges()) continue
                                    EventQueue.invokeLater {
                                        if (activeRequestControl === control) {
                                            applyBufferUpdate(update, responseLines) { partial ->
                                                responsePartialLine = partial
                                            }
                                        }
                                    }
                                }
                            }
                            val worker = thread {
                                sendRequestStreaming(
                                    method = method,
                                    url = url,
                                    body = bodyText,
                                    headersText = headersText,
                                    control = control,
                                    onSseDetected = { isSse ->
                                        EventQueue.invokeLater {
                                            if (activeRequestControl === control) isSseResponse = isSse
                                        }
                                    },
                                    onChunk = { chunk ->
                                        if (activeRequestControl === control && !control.cancelled) {
                                            control.lineBuffer.append(chunk)
                                        }
                                    }
                                )
                                EventQueue.invokeLater {
                                    if (activeRequestControl === control) {
                                        control.finished = true
                                        applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { partial ->
                                            responsePartialLine = partial
                                        }
                                        isLoading = false
                                        isSseResponse = false
                                        activeRequestControl = null
                                        activeRequestThread = null
                                    }
                                }
                                flusher.interrupt()
                            }
                            activeRequestThread = worker
                        } else {
                            val control = activeRequestControl
                            if (control != null) {
                                control.cancelled = true
                                closeQuietly(control.activeInput)
                                control.lineBuffer.append("\n[请求已取消]\n")
                                applyBufferUpdate(control.lineBuffer.drainUpdate(), responseLines) { partial ->
                                    responsePartialLine = partial
                                }
                            }
                            activeRequestThread?.interrupt()
                            isLoading = false
                            isSseResponse = false
                            activeRequestControl = null
                            activeRequestThread = null
                        }
                    },
                    enabled = true
                ) {
                    Text(if (isLoading) "取消请求" else "发送请求")
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        value = headersText,
                        onValueChange = { headersText = it },
                        label = { Text("Headers（每行：Key: Value）") },
                        enabled = !isLoading
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        label = { Text("Body（POST 时生效）") },
                        enabled = !isLoading
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
                        state = responseListState
                    ) {
                        items(responseLines) { line ->
                            Text(line)
                        }
                        responsePartialLine?.let { partial ->
                            item("partial") {
                                Text(partial)
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(responseListState)
                    )
                }
            }
        }
    }

    LaunchedEffect(responseLines.size, responsePartialLine, isSseResponse) {
        if (isSseResponse) {
            val total = responseLines.size + if (responsePartialLine != null) 1 else 0
            if (total > 0) {
                responseListState.scrollToItem(total - 1)
            }
        }
    }
}

private const val UI_REFRESH_INTERVAL_MS = 100L
private val HTTP_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

private fun sendRequestStreaming(
    method: String,
    url: String,
    body: String,
    headersText: String,
    control: RequestControl,
    onSseDetected: (Boolean) -> Unit,
    onChunk: (String) -> Unit
) {
    try {
        if (control.cancelled) return
        val client = HttpClient.newHttpClient()
        val builder = HttpRequest.newBuilder().uri(URI.create(url.trim()))

        parseHeaders(headersText).forEach { (name, value) ->
            builder.header(name, value)
        }

        val request = when (method) {
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()
            else -> builder.GET().build()
        }

        if (control.cancelled) return
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        val isSse = contentType.contains("text/event-stream", ignoreCase = true)
        onSseDetected(isSse)
        onChunk("状态码: ${response.statusCode()}\n")
        onChunk("Content-Type: $contentType\n\n")

        response.body().use { input ->
            control.activeInput = input
            if (isSse) {
                onChunk("SSE 流式响应中...\n\n")
                BufferedReader(InputStreamReader(input)).use { reader ->
                    while (true) {
                        if (control.cancelled || Thread.currentThread().isInterrupted) break
                        val line = reader.readLine() ?: break
                        onChunk("$line\n")
                    }
                }
                if (!control.cancelled) onChunk("\n[SSE 连接已结束]")
            } else {
                val reader = InputStreamReader(input)
                val buffer = CharArray(2048)
                while (true) {
                    if (control.cancelled || Thread.currentThread().isInterrupted) break
                    val readCount = reader.read(buffer)
                    if (readCount <= 0) break
                    onChunk(String(buffer, 0, readCount))
                }
            }
        }
    } catch (_: InterruptedException) {
        if (!control.cancelled) onChunk("\n[请求已取消]\n")
    } catch (e: Exception) {
        if (!control.cancelled) {
            onSseDetected(false)
            onChunk("请求失败: ${e.message}")
        }
    } finally {
        control.activeInput = null
        onSseDetected(false)
    }
}

private fun parseHeaders(headersText: String): List<Pair<String, String>> {
    return headersText
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val splitIndex = line.indexOf(':')
            if (splitIndex <= 0 || splitIndex == line.lastIndex) {
                null
            } else {
                val name = line.substring(0, splitIndex).trim()
                val value = line.substring(splitIndex + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) name to value else null
            }
        }
}

private fun hexToColor(hex: String): Color {
    val value = hex.removePrefix("#")
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        else -> throw IllegalArgumentException("颜色格式错误: $hex，需为 #RRGGBB 或 #AARRGGBB")
    }

    val alpha = argb.substring(0, 2).toInt(16)
    val red = argb.substring(2, 4).toInt(16)
    val green = argb.substring(4, 6).toInt(16)
    val blue = argb.substring(6, 8).toInt(16)
    return Color(red, green, blue, alpha)
}

private fun hexToColorCode(hex: String): ULong {
    val value = hex.removePrefix("#")
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        else -> throw IllegalArgumentException("颜色格式错误: $hex，需为 #RRGGBB 或 #AARRGGBB")
    }
    return ("0x$argb").removePrefix("0x").toULong(16)
}

private data class BufferUpdate(
    val newLines: List<String>,
    val partialLine: String?
) {
    fun hasChanges(): Boolean = newLines.isNotEmpty() || partialLine != null
}

private class ResponseLineBuffer {
    private val pendingLines = mutableListOf<String>()
    private val currentLine = StringBuilder()
    private var emittedPartial: String? = null

    @Synchronized
    fun append(text: String) {
        var start = 0
        while (start < text.length) {
            val index = text.indexOf('\n', start)
            if (index == -1) {
                currentLine.append(text, start, text.length)
                break
            }
            currentLine.append(text, start, index)
            pendingLines += currentLine.toString()
            currentLine.setLength(0)
            start = index + 1
        }
    }

    @Synchronized
    fun drainUpdate(): BufferUpdate {
        val newLines = if (pendingLines.isNotEmpty()) pendingLines.toList() else emptyList()
        pendingLines.clear()

        val current = currentLine.toString()
        val partial = current.takeIf { it.isNotEmpty() }
        val partialChanged = partial != emittedPartial
        emittedPartial = partial

        return BufferUpdate(
            newLines = newLines,
            partialLine = if (partialChanged) partial else null
        )
    }
}

private data class CurlRequest(
    val method: String,
    val url: String,
    val headers: List<String>,
    val body: String
)

private class RequestControl {
    @Volatile
    var cancelled: Boolean = false

    @Volatile
    var activeInput: InputStream? = null

    @Volatile
    var finished: Boolean = false

    val lineBuffer = ResponseLineBuffer()
}

private fun applyBufferUpdate(
    update: BufferUpdate,
    responseLines: MutableList<String>,
    setPartial: (String?) -> Unit
) {
    if (update.newLines.isNotEmpty()) {
        responseLines.addAll(update.newLines)
    }
    if (update.partialLine != null || update.newLines.isNotEmpty()) {
        setPartial(update.partialLine)
    }
}

private fun setSingleResponseMessage(responseLines: MutableList<String>, message: String) {
    responseLines.clear()
    responseLines += message
}

private fun closeQuietly(input: InputStream?) {
    try {
        input?.close()
    } catch (_: Exception) {
    }
}

private fun readClipboardText(): String {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val data = clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
    if (data.isBlank()) throw IllegalArgumentException("剪贴板为空")
    return data.trim()
}

private fun parseCurlCommand(curlCommand: String): CurlRequest {
    val command = curlCommand.replace("\\\n", " ").trim()
    val tokens = tokenizeShellCommand(command)
    if (tokens.isEmpty() || tokens.first() != "curl") {
        throw IllegalArgumentException("剪贴板内容不是 sh 风格的 curl 命令")
    }

    var method = "GET"
    var url = ""
    val headers = mutableListOf<String>()
    val bodyParts = mutableListOf<String>()
    var hasExplicitMethod = false

    var i = 1
    while (i < tokens.size) {
        when (val token = tokens[i]) {
            "-X", "--request" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少请求方法")
                method = next.uppercase()
                hasExplicitMethod = true
                i += 2
            }
            "-H", "--header" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少 Header 值")
                headers += next
                i += 2
            }
            "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少 Body 值")
                bodyParts += next
                i += 2
            }
            "--url" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少 URL")
                url = next
                i += 2
            }
            else -> {
                if (token.startsWith("http://") || token.startsWith("https://")) {
                    url = token
                } else if (token.uppercase() in HTTP_METHODS && !hasExplicitMethod) {
                    method = token.uppercase()
                }
                i += 1
            }
        }
    }

    if (url.isBlank()) throw IllegalArgumentException("未解析到 URL")
    if (!hasExplicitMethod && bodyParts.isNotEmpty()) {
        method = "POST"
    }

    return CurlRequest(
        method = method,
        url = url,
        headers = headers,
        body = bodyParts.joinToString("&")
    )
}

private fun tokenizeShellCommand(command: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false

    for (ch in command) {
        when {
            escaped -> {
                current.append(ch)
                escaped = false
            }
            ch == '\\' && !inSingleQuote -> escaped = true
            ch == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
            ch == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
            ch.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.setLength(0)
                }
            }
            else -> current.append(ch)
        }
    }

    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

fun main() = application {
    Window(title = "Api-X", onCloseRequest = ::exitApplication) {
        App()
    }
}
