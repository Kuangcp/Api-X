package mcp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val mcpWireJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val mcpDisplayJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

data class McpDebugResult(
    val exitCode: Int?,
    val bytes: Long,
)

data class McpToolCallRequest(
    val name: String,
    val arguments: JsonElement,
)

fun runMcpStdioDebug(
    commandLine: String,
    envLines: List<Pair<String, String>>,
    toolCallBody: String,
    timeoutMs: Long = 30_000L,
    isCancelled: () -> Boolean = { false },
    onChunk: (String) -> Unit,
): McpDebugResult {
    val args = splitCommandLine(commandLine)
    require(args.isNotEmpty()) { "MCP STDIO command is empty" }
    val startedAt = System.currentTimeMillis()
    var bytes = 0L
    fun emit(text: String) {
        bytes += text.toByteArray(StandardCharsets.UTF_8).size.toLong()
        onChunk(text)
    }

    emit("[MCP] STDIO command: ${args.joinToString(" ")}\n")
    val process = ProcessBuilder(args).apply {
        val env = environment()
        for ((key, value) in envLines) {
            if (key.isNotBlank()) env[key] = value
        }
    }.start()

    val queue = LinkedBlockingQueue<String>()
    val stdoutThread = Thread {
        BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).useLines { lines ->
            lines.forEach { queue.offer(it) }
        }
    }.also { it.isDaemon = true; it.start() }
    val stderr = StringBuilder()
    val stderrThread = Thread {
        BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).useLines { lines ->
            lines.forEach { stderr.appendLine(it) }
        }
    }.also { it.isDaemon = true; it.start() }

    BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)).use { writer ->
        fun send(id: Int?, method: String, params: JsonObject? = null) {
            val message = buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }
            val line = mcpWireJson.encodeToString(JsonElement.serializer(), message)
            val display = mcpDisplayJson.encodeToString(JsonElement.serializer(), message)
            emit("\n>>> $method${if (id != null) " #$id" else ""}\n$display\n")
            writer.write(line)
            writer.newLine()
            writer.flush()
        }

        fun await(id: Int, label: String): JsonObject? {
            while (!isCancelled()) {
                if (System.currentTimeMillis() - startedAt > timeoutMs) {
                    emit("\n[MCP] Timeout while waiting for $label\n")
                    return null
                }
                val line = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (line.isBlank()) continue
                emit("\n<<< $line\n")
                val obj = runCatching { mcpWireJson.parseToJsonElement(line).jsonObject }.getOrNull()
                if (obj == null) continue
                val gotId = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (gotId == id) return obj
            }
            return null
        }

        send(
            1,
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2025-03-26")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "api-x")
                    put("version", "mcp-debug")
                })
            },
        )
        await(1, "initialize")
        send(null, "notifications/initialized")
        send(2, "tools/list")
        val toolsResponse = await(2, "tools/list")
        emitToolSummary(toolsResponse, ::emit)

        parseToolCall(toolCallBody)?.let { call ->
            send(
                3,
                "tools/call",
                buildJsonObject {
                    put("name", call.name)
                    put("arguments", call.arguments)
                },
            )
            await(3, "tools/call")
        }
    }

    val exitCode = waitOrDestroy(process, 1000L)
    stdoutThread.join(200L)
    stderrThread.join(200L)
    if (stderr.isNotBlank()) emit("\n[MCP stderr]\n$stderr")
    emit("\n[MCP] Process ${if (exitCode == null) "stopped" else "exited: $exitCode"}\n")
    return McpDebugResult(exitCode = exitCode, bytes = bytes)
}

fun runMcpSseDebug(
    sseUrl: String,
    headerLines: List<Pair<String, String>>,
    toolCallBody: String,
    timeoutMs: Long = 30_000L,
    isCancelled: () -> Boolean = { false },
    onChunk: (String) -> Unit,
): McpDebugResult {
    val startedAt = System.currentTimeMillis()
    var bytes = 0L
    fun emit(text: String) {
        bytes += text.toByteArray(StandardCharsets.UTF_8).size.toLong()
        onChunk(text)
    }

    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val baseUri = URI.create(sseUrl)
    val messageQueue = LinkedBlockingQueue<String>()
    val endpointQueue = LinkedBlockingQueue<String>()
    val sseThread = Thread {
        try {
            val requestBuilder = HttpRequest.newBuilder(baseUri)
                .GET()
                .header("Accept", "text/event-stream")
            for ((key, value) in headerLines) {
                if (key.isNotBlank()) requestBuilder.header(key, value)
            }
            val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
            emit("[MCP] SSE connected: HTTP ${response.statusCode()}\n")
            BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8)).use { reader ->
                var eventType = "message"
                val dataLines = mutableListOf<String>()
                fun flush() {
                    if (dataLines.isEmpty()) {
                        eventType = "message"
                        return
                    }
                    val data = dataLines.joinToString("\n")
                    emit("\n<<< SSE $eventType\n$data\n")
                    when (eventType) {
                        "endpoint" -> endpointQueue.offer(data)
                        "message" -> messageQueue.offer(data)
                        else -> messageQueue.offer(data)
                    }
                    eventType = "message"
                    dataLines.clear()
                }
                while (!isCancelled()) {
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
            if (!isCancelled()) emit("\n[MCP SSE error] ${e.message ?: e::class.simpleName}\n")
        }
    }.also { it.isDaemon = true; it.start() }

    fun awaitEndpoint(): URI? {
        while (!isCancelled()) {
            if (System.currentTimeMillis() - startedAt > timeoutMs) return null
            val endpoint = endpointQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            return baseUri.resolve(endpoint.trim())
        }
        return null
    }

    val postUri = awaitEndpoint()
    if (postUri == null) {
        emit("\n[MCP] Timeout while waiting for SSE endpoint event\n")
        return McpDebugResult(exitCode = null, bytes = bytes)
    }
    emit("\n[MCP] POST endpoint: $postUri\n")

    fun post(id: Int?, method: String, params: JsonObject? = null) {
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        val line = mcpWireJson.encodeToString(JsonElement.serializer(), message)
        val display = mcpDisplayJson.encodeToString(JsonElement.serializer(), message)
        emit("\n>>> $method${if (id != null) " #$id" else ""}\n$display\n")
        val builder = HttpRequest.newBuilder(postUri)
            .POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        for ((key, value) in headerLines) {
            if (key.isNotBlank() && !key.equals("Content-Type", ignoreCase = true)) builder.header(key, value)
        }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        emit("[MCP] POST HTTP ${response.statusCode()}\n")
        val body = response.body().trim()
        if (body.isNotEmpty()) {
            emit("\n<<< POST body\n$body\n")
            messageQueue.offer(body)
        }
    }

    fun await(id: Int, label: String): JsonObject? {
        while (!isCancelled()) {
            if (System.currentTimeMillis() - startedAt > timeoutMs) {
                emit("\n[MCP] Timeout while waiting for $label\n")
                return null
            }
            val line = messageQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val obj = runCatching { mcpWireJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            val gotId = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (gotId == id) return obj
        }
        return null
    }

    post(
        1,
        "initialize",
        buildJsonObject {
            put("protocolVersion", "2025-03-26")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", "api-x")
                put("version", "mcp-debug")
            })
        },
    )
    await(1, "initialize")
    post(null, "notifications/initialized")
    post(2, "tools/list")
    val toolsResponse = await(2, "tools/list")
    emitToolSummary(toolsResponse, ::emit)

    parseToolCall(toolCallBody)?.let { call ->
        post(
            3,
            "tools/call",
            buildJsonObject {
                put("name", call.name)
                put("arguments", call.arguments)
            },
        )
        await(3, "tools/call")
    }

    sseThread.interrupt()
    emit("\n[MCP] SSE debug finished\n")
    return McpDebugResult(exitCode = 0, bytes = bytes)
}

private fun emitToolSummary(response: JsonObject?, emit: (String) -> Unit) {
    val tools = response?.get("result")?.jsonObject?.get("tools") as? JsonArray ?: return
    emit("\n[MCP tools]\n")
    for (tool in tools) {
        val obj = tool as? JsonObject ?: continue
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
        val desc = obj["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        emit("- $name${desc?.let { ": $it" } ?: ""}\n")
    }
}

private fun parseToolCall(body: String): McpToolCallRequest? {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null
    val obj = runCatching { mcpWireJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null
    val name = obj["tool"]?.jsonPrimitive?.contentOrNull
        ?: obj["name"]?.jsonPrimitive?.contentOrNull
        ?: return null
    val arguments = obj["arguments"] ?: obj["args"] ?: JsonObject(emptyMap())
    return McpToolCallRequest(name = name, arguments = if (arguments is JsonNull) JsonObject(emptyMap()) else arguments)
}

private fun waitOrDestroy(process: Process, waitMs: Long): Int? {
    if (process.waitFor(waitMs, TimeUnit.MILLISECONDS)) return process.exitValue()
    process.destroy()
    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
    return null
}

private fun splitCommandLine(commandLine: String): List<String> {
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