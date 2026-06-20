package db

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mcp.McpCatalogSummary
import mcp.McpPromptSummary
import mcp.McpResourceSummary
import mcp.McpToolSummary
import kotlin.io.path.notExists

object McpCatalogStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun requestDir(requestId: String): Path =
        AppPaths.requestArtifactsRoot().resolve(sanitizeRequestId(requestId))

    private fun catalogFile(requestId: String): Path =
        requestDir(requestId).resolve("mcp").resolve("catalog.json")

    private fun sanitizeRequestId(id: String): String {
        val t = id.trim()
        require(t.isNotEmpty() && !t.contains("..") && t.none { it == '/' || it == '\\' }) {
            "Illegal request id"
        }
        return t
    }

    fun loadCatalog(requestId: String): McpCatalogSummary? {
        val id = requestId.trim()
        if (id.isEmpty()) return null
        val file = runCatching { catalogFile(id) }.getOrNull() ?: return null
        if (file.notExists() || !Files.isRegularFile(file)) return null
        return runCatching {
            val root = json.parseToJsonElement(Files.readString(file, Charsets.UTF_8)).jsonObject
            McpCatalogSummary(
                tools = root["tools"]?.jsonArray?.mapNotNull(::readTool).orEmpty(),
                resources = root["resources"]?.jsonArray?.mapNotNull(::readResource).orEmpty(),
                prompts = root["prompts"]?.jsonArray?.mapNotNull(::readPrompt).orEmpty(),
            )
        }.getOrNull()
    }

    fun saveCatalog(requestId: String, catalog: McpCatalogSummary) {
        val id = requestId.trim()
        if (id.isEmpty() || catalog.isEmpty) return
        val file = catalogFile(id)
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            json.encodeToString(JsonElement.serializer(), catalog.toJsonObject()),
            Charsets.UTF_8,
        )
    }

    private fun McpCatalogSummary.toJsonObject(): JsonObject = buildJsonObject {
        put("tools", buildJsonArray {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    tool.inputSchema?.let { put("inputSchema", it) }
                })
            }
        })
        put("resources", buildJsonArray {
            resources.forEach { resource ->
                add(buildJsonObject {
                    put("uri", resource.uri)
                    put("name", resource.name)
                    put("description", resource.description)
                    put("mimeType", resource.mimeType)
                })
            }
        })
        put("prompts", buildJsonArray {
            prompts.forEach { prompt ->
                add(buildJsonObject {
                    put("name", prompt.name)
                    put("description", prompt.description)
                    prompt.arguments?.let { put("arguments", it) }
                })
            }
        })
    }

    private fun readTool(element: JsonElement): McpToolSummary? {
        val obj = element as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return McpToolSummary(
            name = name,
            description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            inputSchema = obj["inputSchema"] as? JsonObject,
        )
    }

    private fun readResource(element: JsonElement): McpResourceSummary? {
        val obj = element as? JsonObject ?: return null
        val uri = obj["uri"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return McpResourceSummary(
            uri = uri,
            name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: uri,
            description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun readPrompt(element: JsonElement): McpPromptSummary? {
        val obj = element as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return McpPromptSummary(
            name = name,
            description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            arguments = obj["arguments"] as? JsonArray,
        )
    }
}
