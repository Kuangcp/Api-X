package http

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

private val RESTRICTED_HEADERS = setOf(
    "connection",
    "content-length",
    "expect",
    "host",
    "upgrade"
)

/** 非 HTTP 状态（连接失败、解析错误等）时在状态区展示的占位符。 */
const val HttpExchangeErrorStatusMark = "✕"

private val streamingHttpClient: HttpClient by lazy {
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .proxy(ApiXProxySelector)
        .build()
}

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

/** 行首 `! ` 表示该行 Header 仅编辑、不随请求发送（与表单取消勾选一致）。 */
fun parseHeadersForSend(headersText: String): List<Pair<String, String>> =
    headersText
        .lines()
        .map { it.trim().trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .filterNot { it.startsWith("! ") }
        .mapNotNull { parseHeaderLine(it) }

/** 与 [sendRequestStreaming] 一致：解析编辑器头后去掉 Java HttpClient 不允许设置的逐跳头。 */
fun headersAppliedByHttpClient(headersText: String): List<Pair<String, String>> =
    parseHeadersForSend(headersText).filterNot { (name, _) ->
        name.lowercase() in RESTRICTED_HEADERS
    }

/** 展示「实际发出」的请求：METHOD + URL、过滤后的请求头、正文（与发送逻辑一致）。 */
fun formatActualRequestPlainText(method: String, url: String, headersText: String, body: String): String {
    val m = method.trim().uppercase()
    val u = url.trim()
    val pairs = headersAppliedByHttpClient(headersText)
    val headerBlock = pairs.joinToString("\n") { "${it.first}: ${it.second}" }
    return buildString {
        append(m)
        append(' ')
        append(u)
        append("\n\n")
        if (headerBlock.isNotBlank()) {
            append(headerBlock)
        } else {
            append("(无请求头)")
        }
        if (body.isNotBlank()) {
            append("\n\n")
            append(body)
        }
    }
}

fun parseHeaders(headersText: String): List<Pair<String, String>> = parseHeadersForSend(headersText)

/** 拆成可进表单的合法行（含是否发送），与无法解析的孤儿行。 */
fun splitHeadersForEditor(headersText: String): Pair<List<Triple<String, String, Boolean>>, List<String>> {
    val valid = mutableListOf<Triple<String, String, Boolean>>()
    val invalid = mutableListOf<String>()
    for (raw in headersText.lines()) {
        val trimmed = raw.trim().trimEnd('\r')
        if (trimmed.isEmpty()) continue
        val disabled = trimmed.startsWith("! ")
        val content = if (disabled) trimmed.removePrefix("! ").trimStart() else trimmed
        val parsed = parseHeaderLine(content)
        if (parsed != null) {
            valid.add(Triple(parsed.first, parsed.second, !disabled))
        } else {
            invalid.add(trimmed)
        }
    }
    return valid to invalid
}

/**
 * 解压后落盘 HAR 时去掉与「已解码正文」不一致的逐跳/传输相关头，避免与 `text` 正文矛盾。
 */
fun responseHeaderLinesForHar(wireHeaders: List<String>, bodyWasDecoded: Boolean): List<String> {
    if (!bodyWasDecoded) return wireHeaders
    return wireHeaders.filter { line ->
        val name = parseHeaderLine(line)?.first?.lowercase() ?: return@filter true
        name !in OMIT_FROM_HAR_AFTER_DECODE
    }
}

private val OMIT_FROM_HAR_AFTER_DECODE = setOf("content-encoding", "content-length", "transfer-encoding")

private fun parseContentEncodingTokens(headers: HttpHeaders): List<String> {
    val out = mutableListOf<String>()
    for ((name, values) in headers.map()) {
        if (!name.equals("Content-Encoding", ignoreCase = true)) continue
        for (v in values) {
            for (part in v.split(',')) {
                val t = part.trim().lowercase()
                if (t.isNotEmpty()) out.add(t)
            }
        }
    }
    return out
}

/**
 * 按 Content-Encoding 链（自外向内解压）包装流；支持 gzip / x-gzip / deflate。
 * 若声明了 gzip 但正文已是解压后的明文（部分 HttpClient 行为），则不再套 GZIPInputStream。
 */
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
    control.responseBodyDecodedForHar = true
    var stream: InputStream = raw
    var firstGzipLayer = true
    for (token in supported.asReversed()) {
        when (token) {
            "gzip", "x-gzip" -> {
                stream = if (firstGzipLayer) {
                    firstGzipLayer = false
                    val buf = BufferedInputStream(stream)
                    buf.mark(2)
                    val a = buf.read()
                    val b = buf.read()
                    buf.reset()
                    if (a == 0x1f && b == 0x8b) GZIPInputStream(buf) else buf
                } else {
                    GZIPInputStream(stream)
                }
            }
            "deflate" -> {
                stream = InflaterInputStream(BufferedInputStream(stream), Inflater())
                firstGzipLayer = false
            }
        }
    }
    return stream
}

private val DECODING_CHAIN = setOf("gzip", "x-gzip", "deflate")

fun joinHeadersEditor(validRows: List<Triple<String, String, Boolean>>, orphanLines: List<String>): String {
    val rowsPart = validRows.filter { it.first.isNotBlank() }.joinToString("\n") { (k, v, enabled) ->
        if (enabled) "$k: $v" else "! $k: $v"
    }
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
        val client = streamingHttpClient
        val builder = HttpRequest.newBuilder().uri(URI.create(url.trim()))

        val allowedHeaders = headersAppliedByHttpClient(headersText)
        allowedHeaders.forEach { (name, value) ->
            builder.header(name, value)
        }

        val m = method.trim().uppercase()
        val bodyPub = HttpRequest.BodyPublishers.ofString(body)
        val noBody = HttpRequest.BodyPublishers.noBody()
        val request = when (m) {
            "GET" -> builder.GET().build()
            "POST" -> builder.POST(bodyPub).build()
            "PUT" -> builder.PUT(bodyPub).build()
            "DELETE" ->
                if (body.isBlank()) builder.DELETE().build()
                else builder.method("DELETE", bodyPub).build()
            "OPTIONS" -> builder.method("OPTIONS", noBody).build()
            "HEAD" -> builder.method("HEAD", noBody).build()
            "PATCH" -> builder.method("PATCH", bodyPub).build()
            else ->
                if (body.isNotBlank()) builder.method(m, bodyPub).build()
                else builder.method(m, noBody).build()
        }

        if (control.cancelled) return
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        control.totalBytes = 0
        val headerLines = formatHttpResponseHeaders(response.headers())
        control.responseHeaderSnapshot = headerLines
        onResponseHeaders(headerLines)
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        val isSse = contentType.contains("text/event-stream", ignoreCase = true)
        control.responseWasSse = isSse
        onSseDetected(isSse)
        val code = response.statusCode()
        control.responseStatusCode = code
        onStatusCode(code)
        if (!isSse) {
            onResponseTime(System.currentTimeMillis() - control.startTimeMs)
        }

        val rawBody = response.body()
        val stream = if (isSse) rawBody else wrapResponseBodyStream(rawBody, response.headers(), control)
        try {
            control.activeInput = stream
            if (isSse) {
                onChunk("SSE 流式响应中...\n\n")
                var firstSseEventArrived = false
                BufferedReader(InputStreamReader(stream)).use { reader ->
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
                val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
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
        } finally {
            control.activeInput = null
            closeQuietly(stream)
        }
    } catch (_: InterruptedException) {
        if (!control.cancelled) onChunk("\n[请求已取消]\n")
    } catch (e: Exception) {
        if (!control.cancelled) {
            control.requestFailed = true
            onSseDetected(false)
            val detail = e.message?.ifBlank { null } ?: e.javaClass.simpleName
            onChunk("请求失败: $detail")
        }
    } finally {
        if (control.activeInput != null) {
            control.activeInput = null
        }
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

    /** 连接/读流等异常导致未完成一次有效交换；为 true 时不写入 HAR。 */
    @Volatile
    var requestFailed: Boolean = false

    @Volatile
    var activeInput: InputStream? = null

    @Volatile
    var finished: Boolean = false
    var startTimeMs: Long = 0
    var totalBytes: Long = 0

    val lineBuffer = ResponseLineBuffer()

    /** 与 UI 行列表一致，用于切换请求后仍能按请求维度落盘完整 Body。 */
    private val rawResponseBody = StringBuilder(256)

    @Volatile
    var responseStatusCode: Int = -1

    @Volatile
    var responseHeaderSnapshot: List<String> = emptyList()

    @Volatile
    var responseWasSse: Boolean = false

    /** 为 true 时 HAR 中应去掉 content-encoding 等头，与解压后的正文一致。 */
    @Volatile
    var responseBodyDecodedForHar: Boolean = false

    @Synchronized
    fun appendRawResponse(chunk: String) {
        rawResponseBody.append(chunk)
    }

    fun snapshotRawBodyLines(): List<String> {
        val s = synchronized(this) { rawResponseBody.toString() }
        if (s.isEmpty()) return emptyList()
        return s.lines()
    }
}

fun closeQuietly(input: InputStream?) {
    try {
        input?.close()
    } catch (_: Exception) {
    }
}
