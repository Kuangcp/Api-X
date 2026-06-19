package mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class McpToolSummary(
    val name: String,
    val description: String,
    val inputSchema: JsonObject?,
)

private val mcpToolJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

fun extractMcpToolsFromLog(lines: List<String>, partialLine: String?): List<McpToolSummary> {
    val out = LinkedHashMap<String, McpToolSummary>()
    val allLines = if (partialLine.isNullOrBlank()) lines else lines + partialLine
    for (line in allLines) {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"tools\"")) continue
        val root = runCatching { mcpToolJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: continue
        val tools = root["result"]?.jsonObject?.get("tools") as? JsonArray ?: continue
        for (tool in tools) {
            val obj = tool as? JsonObject ?: continue
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            out[name] = McpToolSummary(
                name = name,
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                inputSchema = obj["inputSchema"] as? JsonObject,
            )
        }
    }
    return out.values.toList()
}

fun buildMcpToolCallTemplate(tool: McpToolSummary): String {
    val args = buildJsonObject {
        val props = tool.inputSchema?.get("properties") as? JsonObject ?: JsonObject(emptyMap())
        for ((key, schema) in props) {
            put(key, placeholderForSchema(schema as? JsonObject))
        }
    }
    val root = buildJsonObject {
        put("tool", tool.name)
        put("arguments", args)
    }
    return mcpToolJson.encodeToString(JsonElement.serializer(), root)
}

private fun placeholderForSchema(schema: JsonObject?): JsonElement {
    val type = schema?.get("type")?.jsonPrimitive?.contentOrNull?.lowercase()
    return when (type) {
        "number", "integer" -> JsonPrimitive(0)
        "boolean" -> JsonPrimitive(false)
        "array" -> JsonArray(emptyList())
        "object" -> JsonObject(emptyMap())
        else -> JsonPrimitive("")
    }
}