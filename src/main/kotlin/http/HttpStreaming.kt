package http

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

private val RESTRICTED_HEADERS = setOf(
    "connection",
    "content-length",
    "expect",
    "host",
    "upgrade"
)

fun formatHttpResponseHeaders(headers: HttpHeaders): List<String> {
    val out = mutableListOf<String>()
    for ((name, values) in headers.map().entries.sortedBy { it.key.lowercase() }) {
        if (name.equals(":status", ignoreCase = true)) continue
        for (value in values) {
            out += "$name: $value"
        }
    }
    return out
}

/**
 * 单行合法 Header：`Name: Value`，Name 非空；允许 Value 为空（如 `Authorization: `）。
 * 不含冒号、或冒号前无名称的行返回 null（发送请求时不采用）。
 */
fun parseHeaderLine(line: String): Pair<String, String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null
    val splitIndex = trimmed.indexOf(':')
    if (splitIndex <= 0) return null
    val name = trimmed.substring(0, splitIndex).trim()
    val value = trimmed.substring(splitIndex + 1).trim()
    if (name.isEmpty()) return null
    return name to value
}

fun parseHeaders(headersText: String): List<Pair<String, String>> =
    headersText
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { parseHeaderLine(it) }

/** 拆成可进表单的合法行，与无法解析的行（切换表单时不生成行，发送时也不带）。 */
fun splitHeadersForEditor(headersText: String): Pair<List<Pair<String, String>>, List<String>> {
    val valid = mutableListOf<Pair<String, String>>()
    val invalid = mutableListOf<String>()
    for (raw in headersText.lines()) {
        val trimmed = raw.trim().trimEnd('\r')
        if (trimmed.isEmpty()) continue
        val parsed = parseHeaderLine(trimmed)
        if (parsed != null) valid.add(parsed)
        else invalid.add(trimmed)
    }
    return valid to invalid
}

fun joinHeadersEditor(validRows: List<Pair<String, String>>, orphanLines: List<String>): String {
    val rowsPart = validRows.filter { it.first.isNotBlank() }.joinToString("\n") { "${it.first}: ${it.second}" }
    val orphanPart = orphanLines.filter { it.isNotBlank() }.joinToString("\n")
    return when {
        rowsPart.isEmpty() && orphanPart.isEmpty() -> ""
        rowsPart.isEmpty() -> orphanPart
        orphanPart.isEmpty() -> rowsPart
        else -> "$rowsPart\n$orphanPart"
    }
}

fun sendRequestStreaming(
    method: String,
    url: String,
    body: String,
    headersText: String,
    control: RequestControl,
    onSseDetected: (Boolean) -> Unit,
    onStatusCode: (Int) -> Unit,
    onResponseTime: (Long) -> Unit,
    onProgress: (Long) -> Unit,
    onResponseHeaders: (List<String>) -> Unit,
    onChunk: (String) -> Unit
) {
    try {
        if (control.cancelled) return
        val client = HttpClient.newHttpClient()
        val builder = HttpRequest.newBuilder().uri(URI.create(url.trim()))

        val allowedHeaders = parseHeaders(headersText).filterNot { (name, _) ->
            name.lowercase() in RESTRICTED_HEADERS
        }
        allowedHeaders.forEach { (name, value) ->
            builder.header(name, value)
        }

        val request = when (method) {
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build()
            else -> builder.GET().build()
        }

        if (control.cancelled) return
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        control.totalBytes = 0
        onResponseHeaders(formatHttpResponseHeaders(response.headers()))
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        val isSse = contentType.contains("text/event-stream", ignoreCase = true)
        onSseDetected(isSse)
        onStatusCode(response.statusCode())
        if (!isSse) {
            onResponseTime(System.currentTimeMillis() - control.startTimeMs)
        }

        response.body().use { input ->
            control.activeInput = input
            if (isSse) {
                onChunk("SSE 流式响应中...\n\n")
                var firstSseEventArrived = false
                BufferedReader(InputStreamReader(input)).use { reader ->
                    while (true) {
                        if (control.cancelled || Thread.currentThread().isInterrupted) break
                        val line = reader.readLine() ?: break
                        if (!firstSseEventArrived && line.isNotBlank()) {
                            firstSseEventArrived = true
                            onResponseTime(System.currentTimeMillis() - control.startTimeMs)
                        }
                        onChunk("$line\n")
                        control.totalBytes += (line + "\n").toByteArray(StandardCharsets.UTF_8).size.toLong()
                        onProgress(control.totalBytes)
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
                    val chunk = String(buffer, 0, readCount)
                    onChunk(chunk)
                    control.totalBytes += chunk.toByteArray(StandardCharsets.UTF_8).size.toLong()
                    onProgress(control.totalBytes)
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

data class BufferUpdate(
    val newLines: List<String>,
    val partialLine: String?
) {
    fun hasChanges(): Boolean = newLines.isNotEmpty() || partialLine != null
}

class ResponseLineBuffer {
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

class RequestControl {
    @Volatile
    var cancelled: Boolean = false

    @Volatile
    var activeInput: InputStream? = null

    @Volatile
    var finished: Boolean = false
    var startTimeMs: Long = 0
    var totalBytes: Long = 0

    val lineBuffer = ResponseLineBuffer()
}

fun closeQuietly(input: InputStream?) {
    try {
        input?.close()
    } catch (_: Exception) {
    }
}
