package mcp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val mcpLiveWireJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val mcpLiveDisplayJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

interface McpLiveConnection {
    val transportLabel: String
    fun connect(timeoutMs: Long = 30_000L, isCancelled: () -> Boolean, onChunk: (String) -> Unit)
    fun call(
        request: McpJsonRpcRequest,
        timeoutMs: Long = 30_000L,
        isCancelled: () -> Boolean,
        onChunk: (String) -> Unit,
    ): McpDebugResult
    fun close(onChunk: (String) -> Unit = {})
}

fun openMcpLiveConnection(
    commandLine: String,
    envOrHeaders: List<Pair<String, String>>,
): McpLiveConnection {
    val isHttpMcp = commandLine.startsWith("http://", ignoreCase = true) ||
        commandLine.startsWith("https://", ignoreCase = true)
    return if (isHttpMcp) {
        SseLiveConnection(commandLine, envOrHeaders)
    } else {
        StdioLiveConnection(commandLine, envOrHeaders)
    }
}

private abstract class BaseLiveConnection : McpLiveConnection {
    private var nextRequestId = 1
    protected var connected = false

    protected fun nextId(): Int = nextRequestId++

    protected fun requestMessage(id: Int?, method: String, params: JsonObject?, onChunk: (String) -> Unit): JsonObject {
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val display = mcpLiveDisplayJson.encodeToString(JsonElement.serializer(), message)
        onChunk("\n>>> $method${if (id != null) " #$id" else ""}\n$display\n")
        return message
    }

    protected fun initializeAndList(
        timeoutMs: Long,
        isCancelled: () -> Boolean,
        onChunk: (String) -> Unit,
        send: (Int?, String, JsonObject?) -> Unit,
        await: (Int, String) -> JsonObject?,
    ) {
        val initializeId = nextId()
        send(
            initializeId,
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2025-03-26")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "api-x")
                    put("version", "mcp-session")
                })
            },
        )
        awaitOrThrow(await, initializeId, "initialize", timeoutMs, isCancelled)
        send(null, "notifications/initialized", null)

        val toolsId = nextId()
        send(toolsId, "tools/list", null)
        awaitOrThrow(await, toolsId, "tools/list", timeoutMs, isCancelled)

        val resourcesId = nextId()
        send(resourcesId, "resources/list", null)
        awaitOrThrow(await, resourcesId, "resources/list", timeoutMs, isCancelled)

        val promptsId = nextId()
        send(promptsId, "prompts/list", null)
        awaitOrThrow(await, promptsId, "prompts/list", timeoutMs, isCancelled)
        connected = true
    }

    private fun awaitOrThrow(
        await: (Int, String) -> JsonObject?,
        id: Int,
        label: String,
        timeoutMs: Long,
        isCancelled: () -> Boolean,
    ): JsonObject {
        if (isCancelled()) throw IllegalStateException("MCP request cancelled")
        return await(id, label) ?: throw IllegalStateException("Timeout while waiting for $label (${timeoutMs}ms)")
    }
}

private class StdioLiveConnection(
    private val commandLine: String,
    private val envLines: List<Pair<String, String>>,
) : BaseLiveConnection() {
    override val transportLabel: String = "MCP STDIO"

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val responses = LinkedBlockingQueue<String>()
    private val stderr = StringBuilder()
    private var stdoutThread: Thread? = null
    private var stderrThread: Thread? = null

    override fun connect(timeoutMs: Long, isCancelled: () -> Boolean, onChunk: (String) -> Unit) {
        if (connected) return
        val args = splitLiveCommandLine(commandLine)
        require(args.isNotEmpty()) { "MCP STDIO command is empty" }
        onChunk("[MCP] STDIO command: ${args.joinToString(" ")}\n")
        val started = ProcessBuilder(args).apply {
            val env = environment()
            envLines.forEach { (key, value) ->
                if (key.isNotBlank()) env[key] = value
            }
        }.start()
        process = started
        writer = BufferedWriter(OutputStreamWriter(started.outputStream, StandardCharsets.UTF_8))
        stdoutThread = Thread {
            BufferedReader(InputStreamReader(started.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    responses.offer(line)
                    onChunk("\n<<< $line\n")
                }
            }
        }.also { it.isDaemon = true; it.start() }
        stderrThread = Thread {
            BufferedReader(InputStreamReader(started.errorStream, StandardCharsets.UTF_8)).useLines { lines ->
                lines.forEach { stderr.appendLine(it) }
            }
        }.also { it.isDaemon = true; it.start() }

        initializeAndList(
            timeoutMs = timeoutMs,
            isCancelled = isCancelled,
            onChunk = onChunk,
            send = { id, method, params -> send(id, method, params, onChunk) },
            await = { id, label -> await(id, label, timeoutMs, isCancelled) },
        )
        onChunk("\n[MCP] Connected\n")
    }

    @Synchronized
    override fun call(
        request: McpJsonRpcRequest,
        timeoutMs: Long,
        isCancelled: () -> Boolean,
        onChunk: (String) -> Unit,
    ): McpDebugResult {
        check(connected) { "MCP STDIO is not connected" }
        var bytes = 0L
        fun emit(text: String) {
            bytes += text.toByteArray(StandardCharsets.UTF_8).size.toLong()
            onChunk(text)
        }
        val id = nextId()
        send(id, request.method, request.params, ::emit)
        val response = await(id, request.method, timeoutMs, isCancelled)
        if (response == null) emit("\n[MCP] Timeout while waiting for ${request.method}\n")
        return McpDebugResult(exitCode = 0, bytes = bytes)
    }
    override fun close(onChunk: (String) -> Unit) {
        writer?.runCatching { close() }
        process?.let { waitOrDestroyLive(it, 500L) }
        stdoutThread?.interrupt()
        stderrThread?.interrupt()
        if (stderr.isNotBlank()) onChunk("\n[MCP stderr]\n$stderr")
        onChunk("\n[MCP] Disconnected\n")
        writer = null
        process = null
        connected = false
    }

    private fun send(id: Int?, method: String, params: JsonObject?, onChunk: (String) -> Unit) {
        val message = requestMessage(id, method, params, onChunk)
        val line = mcpLiveWireJson.encodeToString(JsonElement.serializer(), message)
        val activeWriter = writer ?: error("MCP STDIO writer is not ready")
        activeWriter.write(line)
        activeWriter.newLine()
        activeWriter.flush()
    }

    private fun await(id: Int, label: String, timeoutMs: Long, isCancelled: () -> Boolean): JsonObject? {
        val startedAt = System.currentTimeMillis()
        while (!isCancelled()) {
            if (System.currentTimeMillis() - startedAt > timeoutMs) return null
            val line = responses.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val obj = runCatching { mcpLiveWireJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            val gotId = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (gotId == id) return obj
        }
        return null
    }
}

private class SseLiveConnection(
    private val sseUrl: String,
    private val headerLines: List<Pair<String, String>>,
) : BaseLiveConnection() {
    override val transportLabel: String = "MCP SSE"

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    private val baseUri = URI.create(sseUrl)
    private val endpoints = LinkedBlockingQueue<String>()
    private val responses = LinkedBlockingQueue<String>()
    private var postUri: URI? = null
    private var sseThread: Thread? = null
    private var activeInput: InputStream? = null
    @Volatile private var closed = false
    private val responseIdsSeen = mutableSetOf<String>()
    private val responseIdsLock = Any()

    override fun connect(timeoutMs: Long, isCancelled: () -> Boolean, onChunk: (String) -> Unit) {
        if (connected) return
        closed = false
        sseThread = Thread {
            try {
                val requestBuilder = HttpRequest.newBuilder(baseUri)
                    .GET()
                    .header("Accept", "text/event-stream")
                headerLines.forEach { (key, value) ->
                    if (key.isNotBlank()) requestBuilder.header(key, value)
                }
                val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
                onChunk("[MCP] SSE connected: HTTP ${response.statusCode()}\n")
                val input = response.body()
                activeInput = input
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    var eventType = "message"
                    val dataLines = mutableListOf<String>()
                    fun flush() {
                        if (dataLines.isEmpty()) {
                            eventType = "message"
                            return
                        }
                        val data = dataLines.joinToString("\n")
                        if (eventType == "endpoint") {
                            onChunk("\n<<< SSE $eventType\n$data\n")
                            endpoints.offer(data)
                        } else {
                            offerIncomingResponse("SSE $eventType", data, onChunk)
                        }
                        eventType = "message"
                        dataLines.clear()
                    }
                    while (!closed) {
                        val line = reader.readLine() ?: break
                        when {
                            line.isBlank() -> flush()
                            line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                        }
                    }
                    flush()
                }
            } catch (e: Exception) {
                if (!closed) onChunk("\n[MCP SSE error] ${e.message ?: e::class.simpleName}\n")
            }
        }.also { it.isDaemon = true; it.start() }

        postUri = waitForEndpoint(timeoutMs, isCancelled)
        onChunk("\n[MCP] POST endpoint: $postUri\n")
        initializeAndList(
            timeoutMs = timeoutMs,
            isCancelled = isCancelled,
            onChunk = onChunk,
            send = { id, method, params -> post(id, method, params, onChunk) },
            await = { id, label -> await(id, label, timeoutMs, isCancelled) },
        )
        onChunk("\n[MCP] Connected\n")
    }

    @Synchronized
    override fun call(
        request: McpJsonRpcRequest,
        timeoutMs: Long,
        isCancelled: () -> Boolean,
        onChunk: (String) -> Unit,
    ): McpDebugResult {
        check(connected) { "MCP SSE is not connected" }
        var bytes = 0L
        fun emit(text: String) {
            bytes += text.toByteArray(StandardCharsets.UTF_8).size.toLong()
            onChunk(text)
        }
        val id = nextId()
        post(id, request.method, request.params, ::emit)
        val response = await(id, request.method, timeoutMs, isCancelled)
        if (response == null) emit("\n[MCP] Timeout while waiting for ${request.method}\n")
        return McpDebugResult(exitCode = 0, bytes = bytes)
    }
    override fun close(onChunk: (String) -> Unit) {
        closed = true
        activeInput?.runCatching { close() }
        sseThread?.interrupt()
        activeInput = null
        sseThread = null
        postUri = null
        connected = false
        onChunk("\n[MCP] Disconnected\n")
    }

    private fun waitForEndpoint(timeoutMs: Long, isCancelled: () -> Boolean): URI {
        val startedAt = System.currentTimeMillis()
        while (!isCancelled()) {
            if (System.currentTimeMillis() - startedAt > timeoutMs) {
                throw IllegalStateException("Timeout while waiting for SSE endpoint event (${timeoutMs}ms)")
            }
            val endpoint = endpoints.poll(100, TimeUnit.MILLISECONDS) ?: continue
            return baseUri.resolve(endpoint.trim())
        }
        throw IllegalStateException("MCP request cancelled")
    }

    private fun post(id: Int?, method: String, params: JsonObject?, onChunk: (String) -> Unit) {
        val resolvedPostUri = postUri ?: error("MCP SSE POST endpoint is not ready")
        val message = requestMessage(id, method, params, onChunk)
        val line = mcpLiveWireJson.encodeToString(JsonElement.serializer(), message)
        val builder = HttpRequest.newBuilder(resolvedPostUri)
            .POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        headerLines.forEach { (key, value) ->
            if (key.isNotBlank() && !key.equals("Content-Type", ignoreCase = true)) builder.header(key, value)
        }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        onChunk("[MCP] POST HTTP ${response.statusCode()}\n")
        val body = response.body().trim()
        if (body.isNotEmpty()) {
            offerIncomingResponse("POST body", body, onChunk)
        }
    }

    private fun offerIncomingResponse(sourceLabel: String, payload: String, onChunk: (String) -> Unit) {
        val duplicateId = duplicateJsonRpcResponseId(payload)
        if (duplicateId != null) {
            onChunk("\n[MCP] Duplicate response #$duplicateId from $sourceLabel ignored\n")
            return
        }
        onChunk("\n<<< $sourceLabel\n$payload\n")
        responses.offer(payload)
    }

    private fun duplicateJsonRpcResponseId(payload: String): String? {
        val obj = runCatching { mcpLiveWireJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        if (obj["result"] == null && obj["error"] == null) return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        return synchronized(responseIdsLock) {
            if (responseIdsSeen.add(id)) null else id
        }
    }

    private fun await(id: Int, label: String, timeoutMs: Long, isCancelled: () -> Boolean): JsonObject? {
        val startedAt = System.currentTimeMillis()
        while (!isCancelled()) {
            if (System.currentTimeMillis() - startedAt > timeoutMs) return null
            val line = responses.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val obj = runCatching { mcpLiveWireJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            val gotId = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (gotId == id) return obj
        }
        return null
    }
}

private fun waitOrDestroyLive(process: Process, waitMs: Long): Int? {
    if (process.waitFor(waitMs, TimeUnit.MILLISECONDS)) return process.exitValue()
    process.destroy()
    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
    return null
}

private fun splitLiveCommandLine(commandLine: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quote: Char? = null
    var escaped = false
    for (ch in commandLine) {
        when {
            escaped -> {
                cur.append(ch)
                escaped = false
            }
            ch == '\\' -> escaped = true
            quote != null && ch == quote -> quote = null
            quote != null -> cur.append(ch)
            ch == '\'' || ch == '"' -> quote = ch
            ch.isWhitespace() -> {
                if (cur.isNotEmpty()) {
                    out += cur.toString()
                    cur.clear()
                }
            }
            else -> cur.append(ch)
        }
    }
    if (escaped) cur.append('\\')
    if (cur.isNotEmpty()) out += cur.toString()
    return out
}
