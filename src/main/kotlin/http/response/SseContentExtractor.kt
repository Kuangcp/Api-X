package http.response

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class ExtractedSseContent(
    val text: String,
    val chunkCount: Int,
)

private data class SseEvent(
    val eventType: String?,
    val data: String,
)

private data class JsonTextRule(
    val eventTypes: Set<String> = emptySet(),
    val paths: List<String>,
)

private val extractorJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val textRules = listOf(
    JsonTextRule(
        eventTypes = setOf(
            "response.output_text.delta",
            "response.refusal.delta",
            "response.reasoning_summary_text.delta",
        ),
        paths = listOf("delta"),
    ),
    JsonTextRule(paths = listOf("choices[].delta.content")),
    JsonTextRule(paths = listOf("choices[].delta.refusal")),
    JsonTextRule(paths = listOf("choices[].text")),
    JsonTextRule(paths = listOf("message.content")),
    JsonTextRule(paths = listOf("choices[].message.content")),
    JsonTextRule(paths = listOf("output[].content[].text")),
    JsonTextRule(paths = listOf("content[].text")),
    JsonTextRule(paths = listOf("delta.text")),
    JsonTextRule(paths = listOf("content")),
    JsonTextRule(paths = listOf("text")),
    JsonTextRule(paths = listOf("completion")),
)

private const val TIMESTAMP_SEP = " data:"

private fun stripTimestampPrefix(line: String): String {
    val idx = line.indexOf(TIMESTAMP_SEP)
    return if (idx >= 0) line.substring(idx + 1) else line
}

internal fun extractSseRenderableContent(
    responseLines: List<String>,
    responsePartialLine: String?,
    customTextRulePaths: List<String> = emptyList(),
): ExtractedSseContent {
    val cleanedLines = responseLines.map(::stripTimestampPrefix)
    val cleanedPartial = responsePartialLine?.let(::stripTimestampPrefix)
    val events = parseSseEvents(cleanedLines, cleanedPartial).ifEmpty {
        parseBareJsonEvents(cleanedLines, cleanedPartial)
    }
    val chunks = buildList {
        for (event in events) {
            if (event.data == "[DONE]") continue
            val parsed = runCatching { extractorJson.parseToJsonElement(event.data) }.getOrNull()
            if (parsed != null) {
                addAll(extractTextChunks(parsed, event.eventType, customTextRulePaths))
            } else if (event.data.isNotBlank()) {
                add(event.data)
            }
        }
    }
    return ExtractedSseContent(
        text = chunks.joinToString(separator = ""),
        chunkCount = chunks.size,
    )
}

private fun parseSseEvents(lines: List<String>, partialLine: String?): List<SseEvent> {
    val events = mutableListOf<SseEvent>()
    var eventType: String? = null
    val dataLines = mutableListOf<String>()

    fun flush() {
        if (dataLines.isNotEmpty()) {
            events += SseEvent(eventType = eventType, data = dataLines.joinToString("\n"))
        }
        eventType = null
        dataLines.clear()
    }

    fun consume(line: String) {
        val trimmed = line.trimStart()
        when {
            trimmed.isBlank() -> flush()
            trimmed.startsWith("event:") -> eventType = trimmed.removePrefix("event:").trim()
            trimmed.startsWith("data:") -> dataLines += trimmed.removePrefix("data:").trimStart()
        }
    }

    lines.forEach(::consume)
    partialLine?.takeIf { it.isNotBlank() }?.let(::consume)
    flush()
    return events
}

private fun parseBareJsonEvents(lines: List<String>, partialLine: String?): List<SseEvent> {
    val events = mutableListOf<SseEvent>()
    val buffer = StringBuilder()

    fun flushIfJson() {
        val payload = buffer.toString().trim()
        if (payload.isNotEmpty() && runCatching { extractorJson.parseToJsonElement(payload) }.isSuccess) {
            events += SseEvent(eventType = null, data = payload)
        }
        buffer.clear()
    }

    fun consume(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            flushIfJson()
            return
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.equals("[DONE]", ignoreCase = true)) return
        if (buffer.isEmpty() && trimmed != "[DONE]" && !trimmed.startsWith("{") && !trimmed.startsWith("[")) return
        if (trimmed == "[DONE]") {
            events += SseEvent(eventType = null, data = trimmed)
            return
        }
        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(line)
        if (jsonObjectIsComplete(buffer.toString())) flushIfJson()
    }

    lines.forEach(::consume)
    partialLine?.takeIf { it.isNotBlank() }?.let(::consume)
    flushIfJson()
    return events
}

private fun jsonObjectIsComplete(text: String): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    var sawJsonStart = false
    for (ch in text) {
        if (escaped) {
            escaped = false
            continue
        }
        if (inString) {
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = false
            }
            continue
        }
        when (ch) {
            '"' -> inString = true
            '{', '[' -> {
                depth++
                sawJsonStart = true
            }
            '}', ']' -> depth--
        }
    }
    return sawJsonStart && depth == 0 && !inString
}

private fun extractTextChunks(
    element: JsonElement,
    eventType: String?,
    customTextRulePaths: List<String>,
): List<String> {
    val chunks = mutableListOf<String>()
    val customRules = customTextRulePaths.map { JsonTextRule(paths = listOf(it)) }
    for (rule in textRules + customRules) {
        if (rule.eventTypes.isNotEmpty() && eventType !in rule.eventTypes) continue
        for (path in rule.paths) {
            chunks += valuesAtPath(element, path).mapNotNull { it.stringContentOrNull() }
        }
    }
    return chunks
}

private fun valuesAtPath(element: JsonElement, path: String): List<JsonElement> {
    var current = listOf(element)
    for (segment in path.split('.')) {
        val arrayWildcard = segment.endsWith("[]")
        val key = if (arrayWildcard) segment.dropLast(2) else segment
        current = current.flatMap { node ->
            val value = if (key.isEmpty()) node else (node as? JsonObject)?.get(key)
            when {
                value == null -> emptyList()
                arrayWildcard -> (value as? JsonArray)?.toList() ?: emptyList()
                else -> listOf(value)
            }
        }
        if (current.isEmpty()) break
    }
    return current
}

private fun JsonElement.stringContentOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
