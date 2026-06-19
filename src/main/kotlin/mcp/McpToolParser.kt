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

data class McpResourceSummary(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String,
)

data class McpPromptSummary(
    val name: String,
    val description: String,
    val arguments: JsonArray?,
)

data class McpCatalogSummary(
    val tools: List<McpToolSummary> = emptyList(),
    val resources: List<McpResourceSummary> = emptyList(),
    val prompts: List<McpPromptSummary> = emptyList(),
) {
    val isEmpty: Boolean get() = tools.isEmpty() && resources.isEmpty() && prompts.isEmpty()
}

private val mcpToolJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

fun extractMcpToolsFromLog(lines: List<String>, partialLine: String?): List<McpToolSummary> {
    return extractMcpCatalogFromLog(lines, partialLine).tools
}

fun extractMcpCatalogFromLog(lines: List<String>, partialLine: String?): McpCatalogSummary {
    val tools = LinkedHashMap<String, McpToolSummary>()
    val resources = LinkedHashMap<String, McpResourceSummary>()
    val prompts = LinkedHashMap<String, McpPromptSummary>()
    val allLines = if (partialLine.isNullOrBlank()) lines else lines + partialLine
    for (line in allLines) {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"result\"")) continue
        val root = runCatching { mcpToolJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: continue
        val result = root["result"] as? JsonObject ?: continue
        readTools(result, tools)
        readResources(result, resources)
        readPrompts(result, prompts)
    }
    return McpCatalogSummary(
        tools = tools.values.toList(),
        resources = resources.values.toList(),
        prompts = prompts.values.toList(),
    )
}

private fun readTools(result: JsonObject, out: LinkedHashMap<String, McpToolSummary>) {
    val items = result["tools"] as? JsonArray ?: return
    for (item in items) {
        val obj = item as? JsonObject ?: continue
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        out[name] = McpToolSummary(
            name = name,
            description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            inputSchema = obj["inputSchema"] as? JsonObject,
        )
    }
}

private fun readResources(result: JsonObject, out: LinkedHashMap<String, McpResourceSummary>) {
    val items = result["resources"] as? JsonArray ?: return
    for (item in items) {
        val obj = item as? JsonObject ?: continue
        val uri = obj["uri"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        out[uri] = McpResourceSummary(
            uri = uri,
            name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: uri,
            description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
}

private fun readPrompts(result: JsonObject, out: LinkedHashMap<String, McpPromptSummary>) {
    val items = result["prompts"] as? JsonArray ?: return
    for (item in items) {
        val obj = item as? JsonObject ?: continue
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        out[name] = McpPromptSummary(
            name = name,
            description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            arguments = obj["arguments"] as? JsonArray,
        )
    }
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

fun buildMcpResourceReadTemplate(resource: McpResourceSummary): String {
    val root = buildJsonObject {
        put("method", "resources/read")
        put("params", buildJsonObject {
            put("uri", resource.uri)
        })
    }
    return mcpToolJson.encodeToString(JsonElement.serializer(), root)
}

fun buildMcpPromptGetTemplate(prompt: McpPromptSummary): String {
    val args = buildJsonObject {
        prompt.arguments?.forEach { arg ->
            val obj = arg as? JsonObject ?: return@forEach
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@forEach
            put(name, "")
        }
    }
    val root = buildJsonObject {
        put("method", "prompts/get")
        put("params", buildJsonObject {
            put("name", prompt.name)
            put("arguments", args)
        })
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
