package app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import db.McpDraftStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mcp.McpJsonRpcRequest
import mcp.McpPromptSummary
import mcp.McpResourceSummary
import mcp.McpToolSummary

sealed class McpSelection(
    val method: String,
    val identifier: String,
) {
    class Tool(val tool: McpToolSummary) : McpSelection("tools/call", tool.name)
    class Resource(val resource: McpResourceSummary) : McpSelection("resources/read", resource.uri)
    class Prompt(val prompt: McpPromptSummary) : McpSelection("prompts/get", prompt.name)
}

class McpSelectionState {
    var selectedRequestId by mutableStateOf<String?>(null)
        private set
    var selectedItem by mutableStateOf<McpSelection?>(null)
        private set

    fun rememberCurrentDraft(bodyText: String) {
        val requestId = selectedRequestId ?: return
        val item = selectedItem ?: return
        McpDraftStore.saveDraft(requestId, item.method, item.identifier, bodyText)
    }

    fun selectTool(requestId: String, tool: McpToolSummary): String {
        val item = McpSelection.Tool(tool)
        select(requestId, item)
        return loadDraft(requestId, item)?.let { draftOrDefault(item, it) } ?: defaultEditableParams(item)
    }

    fun selectResource(requestId: String, resource: McpResourceSummary): String {
        val item = McpSelection.Resource(resource)
        select(requestId, item)
        return loadDraft(requestId, item)?.let { draftOrDefault(item, it) } ?: defaultEditableParams(item)
    }

    fun selectPrompt(requestId: String, prompt: McpPromptSummary): String {
        val item = McpSelection.Prompt(prompt)
        select(requestId, item)
        return loadDraft(requestId, item)?.let { draftOrDefault(item, it) } ?: defaultEditableParams(item)
    }

    fun buildSelectedRequest(bodyText: String): McpJsonRpcRequest? {
        val item = selectedItem ?: return null
        val editable = editableParamsFor(item, bodyText)
        val args = parseObject(editable).getOrElse { return null }
        return when (item) {
            is McpSelection.Tool -> McpJsonRpcRequest(
                method = item.method,
                params = buildJsonObject {
                    put("name", item.identifier)
                    put("arguments", args)
                },
            )
            is McpSelection.Resource -> McpJsonRpcRequest(
                method = item.method,
                params = buildJsonObject {
                    put("uri", item.identifier)
                },
            )
            is McpSelection.Prompt -> McpJsonRpcRequest(
                method = item.method,
                params = buildJsonObject {
                    put("name", item.identifier)
                    put("arguments", args)
                },
            )
        }
    }

    fun buildSelectedRequestBody(bodyText: String): String? {
        val request = buildSelectedRequest(bodyText) ?: return null
        val root = buildJsonObject {
            put("method", request.method)
            request.params?.let { put("params", it) }
        }
        return mcpSelectionJson.encodeToString(JsonElement.serializer(), root)
    }
    fun editorHintFor(requestId: String?): String? {
        if (requestId == null || selectedRequestId != requestId) return null
        return when (val item = selectedItem ?: return null) {
            is McpSelection.Tool -> "MCP ${item.method} / ${item.identifier} / editing arguments JSON"
            is McpSelection.Resource -> "MCP ${item.method} / ${item.identifier} / uri is fixed from catalog"
            is McpSelection.Prompt -> "MCP ${item.method} / ${item.identifier} / editing arguments JSON"
        }
    }

    private fun select(requestId: String, item: McpSelection) {
        selectedRequestId = requestId
        selectedItem = item
    }

    private fun loadDraft(requestId: String, item: McpSelection): String? {
        return McpDraftStore.loadDraft(requestId, item.method, item.identifier)
    }

    private fun draftOrDefault(item: McpSelection, draft: String): String {
        val editable = editableParamsFor(item, draft)
        val default = defaultEditableParams(item)
        val editableObj = parseObject(editable).getOrNull()
        val defaultObj = parseObject(default).getOrNull()
        if (editableObj != null && editableObj.isEmpty() && defaultObj != null && defaultObj.isNotEmpty()) {
            return default
        }
        return editable
    }

    private fun defaultEditableParams(item: McpSelection): String {
        val obj = when (item) {
            is McpSelection.Tool -> buildToolArguments(item.tool)
            is McpSelection.Resource -> JsonObject(emptyMap())
            is McpSelection.Prompt -> buildPromptArguments(item.prompt)
        }
        return mcpSelectionJson.encodeToString(JsonElement.serializer(), obj)
    }

    private fun editableParamsFor(item: McpSelection, bodyText: String): String {
        val trimmed = bodyText.trim()
        if (trimmed.isEmpty()) return defaultEditableParams(item)
        val obj = parseObject(trimmed).getOrNull() ?: return trimmed
        val editableObj = when (item) {
            is McpSelection.Tool -> {
                val params = obj["params"] as? JsonObject
                val args = params?.get("arguments") ?: obj["arguments"] ?: obj["args"]
                normalizeObject(args) ?: obj
            }
            is McpSelection.Resource -> JsonObject(emptyMap())
            is McpSelection.Prompt -> {
                val params = obj["params"] as? JsonObject
                val args = params?.get("arguments") ?: obj["arguments"] ?: obj["args"]
                normalizeObject(args) ?: obj
            }
        }
        return mcpSelectionJson.encodeToString(JsonElement.serializer(), editableObj)
    }

    private fun parseObject(text: String): Result<JsonObject> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.success(JsonObject(emptyMap()))
        return runCatching { mcpSelectionJson.parseToJsonElement(trimmed).jsonObject }
    }

    private fun normalizeObject(element: JsonElement?): JsonObject? {
        if (element == null) return null
        if (element is JsonNull) return JsonObject(emptyMap())
        return element as? JsonObject
    }

    private fun buildToolArguments(tool: McpToolSummary): JsonObject = buildJsonObject {
        val props = tool.inputSchema?.get("properties") as? JsonObject ?: JsonObject(emptyMap())
        for ((key, schema) in props) {
            put(key, placeholderForSchema(schema as? JsonObject))
        }
    }

    private fun buildPromptArguments(prompt: McpPromptSummary): JsonObject = buildJsonObject {
        prompt.arguments?.forEach { arg ->
            val obj = arg as? JsonObject ?: return@forEach
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@forEach
            put(name, "")
        }
    }
}

private val mcpSelectionJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

private fun placeholderForSchema(schema: JsonObject?): JsonElement {
    val type = schema?.get("type")?.jsonPrimitive?.contentOrNull?.lowercase()
    return when (type) {
        "number", "integer" -> kotlinx.serialization.json.JsonPrimitive(0)
        "boolean" -> kotlinx.serialization.json.JsonPrimitive(false)
        "array" -> kotlinx.serialization.json.JsonArray(emptyList())
        "object" -> JsonObject(emptyMap())
        else -> kotlinx.serialization.json.JsonPrimitive("")
    }
}