package openapi

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tree.PortableCollection
import tree.PortableFolder
import tree.PortableRequest

private val openApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

private val httpMethods = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")

data class OpenApiImportResult(
    val portable: PortableCollection,
    val requestCount: Int,
    val folderCount: Int,
)

fun parseOpenApiToPortableCollection(
    text: String,
    sourceUrl: String,
    collectionName: String,
    collectionId: String,
): OpenApiImportResult {
    val root = runCatching { openApiJson.parseToJsonElement(text).jsonObject }
        .getOrElse { throw IllegalArgumentException("OpenAPI 内容不是合法 JSON: ${it.message}") }
    val paths = root["paths"] as? JsonObject
        ?: throw IllegalArgumentException("OpenAPI 内容缺少 paths")
    val baseUrl = inferBaseUrl(root, sourceUrl)
    val grouped = linkedMapOf<String, MutableList<PortableRequest>>()
    var sort = 0
    paths.forEach { (path, pathItem) ->
        val pathObj = pathItem as? JsonObject ?: return@forEach
        pathObj.forEach { (methodKey, operationValue) ->
            val method = methodKey.lowercase()
            if (method !in httpMethods) return@forEach
            val operation = operationValue as? JsonObject ?: return@forEach
            val tag = firstTag(operation) ?: "Default"
            grouped.getOrPut(tag) { mutableListOf() } += operationToRequest(
                root = root,
                collectionId = collectionId,
                baseUrl = baseUrl,
                sourceUrl = sourceUrl,
                tag = tag,
                method = method.uppercase(),
                path = path,
                operation = operation,
                sortOrder = sort++,
            )
        }
    }
    val folders = grouped.entries.mapIndexed { index, (tag, requests) ->
        PortableFolder(
            id = deterministicId("openapi:$collectionId:folder:$tag"),
            name = tag,
            sortOrder = index,
            metaJson = openApiMetaJson(sourceUrl, "tag", tag),
            requests = requests,
        )
    }
    val portable = PortableCollection(
        name = collectionName,
        collectionMetaJson = openApiCollectionMetaJson(sourceUrl, baseUrl),
        folders = folders,
    )
    return OpenApiImportResult(
        portable = portable,
        requestCount = grouped.values.sumOf { it.size },
        folderCount = folders.size,
    )
}

fun openApiCollectionMetaJson(sourceUrl: String, baseUrl: String? = null): String {
    return openApiJson.encodeToString(JsonElement.serializer(), buildJsonObject {
        put("openapi", buildJsonObject {
            put("sourceUrl", sourceUrl)
            if (!baseUrl.isNullOrBlank()) put("baseUrl", baseUrl)
            put("refreshedAt", System.currentTimeMillis())
        })
    })
}

private fun operationToRequest(
    root: JsonObject,
    collectionId: String,
    baseUrl: String?,
    sourceUrl: String,
    tag: String,
    method: String,
    path: String,
    operation: JsonObject,
    sortOrder: Int,
): PortableRequest {
    val operationId = operation["operationId"]?.jsonPrimitive?.contentOrNull
    val summary = operation["summary"]?.jsonPrimitive?.contentOrNull
    val name = summary?.takeIf { it.isNotBlank() } ?: operationId?.takeIf { it.isNotBlank() } ?: "$method $path"
    val params = collectParameters(root, operation)
    val queryText = params
        .filter { it.location == "query" }
        .joinToString("\n") { "${it.name}: ${it.example}" }
    val headers = buildList {
        add("Accept: application/json")
        val contentType = requestBodyContentType(operation)
        if (contentType != null && method !in setOf("GET", "HEAD")) add("Content-Type: $contentType")
        params.filter { it.location == "header" }.forEach { add("${it.name}: ${it.example}") }
    }.distinct().joinToString("\n")
    val body = requestBodyExample(root, operation)
    val url = listOfNotNull(baseUrl?.trimEnd('/'), path.ensureLeadingSlash()).joinToString("")
    return PortableRequest(
        id = deterministicId("openapi:$collectionId:request:$method:$path"),
        name = name,
        method = method,
        url = url,
        headersText = headers,
        paramsText = queryText,
        bodyText = body,
        sortOrder = sortOrder,
        metaJson = openApiRequestMetaJson(sourceUrl, tag, method, path, operationId),
    )
}

private data class OpenApiParameter(val name: String, val location: String, val example: String)

private fun collectParameters(root: JsonObject, operation: JsonObject): List<OpenApiParameter> {
    val arr = operation["parameters"] as? JsonArray ?: return emptyList()
    return arr.mapNotNull { raw ->
        val p = resolveRef(root, raw) as? JsonObject ?: return@mapNotNull null
        val name = p["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val location = p["in"]?.jsonPrimitive?.contentOrNull ?: "query"
        val schema = resolveRef(root, p["schema"]) as? JsonObject
        val example = p["example"]?.primitiveText() ?: sampleForSchema(root, schema).primitiveText() ?: ""
        OpenApiParameter(name, location, example)
    }
}

private fun requestBodyContentType(operation: JsonObject): String? {
    val content = ((operation["requestBody"] as? JsonObject)?.get("content") as? JsonObject) ?: return null
    return content.keys.firstOrNull { it.contains("json", ignoreCase = true) } ?: content.keys.firstOrNull()
}

private fun requestBodyExample(root: JsonObject, operation: JsonObject): String {
    val content = ((operation["requestBody"] as? JsonObject)?.get("content") as? JsonObject) ?: return ""
    val media = content["application/json"] as? JsonObject
        ?: content.entries.firstOrNull { it.key.contains("json", ignoreCase = true) }?.value as? JsonObject
        ?: content.values.firstOrNull() as? JsonObject
        ?: return ""
    val explicit = media["example"] ?: (media["examples"] as? JsonObject)?.values?.firstOrNull()?.let { (it as? JsonObject)?.get("value") }
    val element = explicit ?: sampleForSchema(root, resolveRef(root, media["schema"]) as? JsonObject)
    if (element == JsonNull) return ""
    return openApiJson.encodeToString(JsonElement.serializer(), element)
}

private fun sampleForSchema(root: JsonObject, schema: JsonObject?): JsonElement {
    if (schema == null) return JsonPrimitive("")
    schema["example"]?.let { return it }
    schema["default"]?.let { return it }
    val resolved = resolveRef(root, schema) as? JsonObject ?: schema
    val type = resolved["type"]?.jsonPrimitive?.contentOrNull
    return when {
        type == "object" || resolved["properties"] is JsonObject -> {
            val props = resolved["properties"] as? JsonObject ?: return buildJsonObject {}
            buildJsonObject {
                props.forEach { (name, value) ->
                    put(name, sampleForSchema(root, resolveRef(root, value) as? JsonObject))
                }
            }
        }
        type == "array" -> buildJsonArray {
            add(sampleForSchema(root, resolveRef(root, resolved["items"]) as? JsonObject))
        }
        type == "integer" -> JsonPrimitive(0)
        type == "number" -> JsonPrimitive(0.0)
        type == "boolean" -> JsonPrimitive(false)
        else -> JsonPrimitive("")
    }
}

private fun resolveRef(root: JsonObject, element: JsonElement?): JsonElement? {
    val obj = element as? JsonObject ?: return element
    val ref = obj["\$ref"]?.jsonPrimitive?.contentOrNull ?: return element
    if (!ref.startsWith("#/")) return element
    return ref.removePrefix("#/").split('/').fold(root as JsonElement?) { cur, part ->
        (cur as? JsonObject)?.get(part.replace("~1", "/").replace("~0", "~"))
    } ?: element
}

private fun firstTag(operation: JsonObject): String? {
    return (operation["tags"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun inferBaseUrl(root: JsonObject, sourceUrl: String): String? {
    val serverUrl = (root["servers"] as? JsonArray)
        ?.firstOrNull()
        ?.jsonObject
        ?.get("url")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
    if (!serverUrl.isNullOrBlank()) return serverUrl
    return runCatching {
        val uri = URI(sourceUrl)
        "${uri.scheme}://${uri.authority}"
    }.getOrNull()
}

private fun JsonElement.primitiveText(): String? {
    return when (this) {
        is JsonPrimitive -> contentOrNull
        else -> openApiJson.encodeToString(JsonElement.serializer(), this)
    }
}

private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"

private fun openApiRequestMetaJson(
    sourceUrl: String,
    tag: String,
    method: String,
    path: String,
    operationId: String?,
): String {
    return openApiJson.encodeToString(JsonElement.serializer(), buildJsonObject {
        put("openapi", buildJsonObject {
            put("sourceUrl", sourceUrl)
            put("tag", tag)
            put("method", method)
            put("path", path)
            if (!operationId.isNullOrBlank()) put("operationId", operationId)
        })
    })
}

private fun openApiMetaJson(sourceUrl: String, kind: String, name: String): String {
    return openApiJson.encodeToString(JsonElement.serializer(), buildJsonObject {
        put("openapi", buildJsonObject {
            put("sourceUrl", sourceUrl)
            put("kind", kind)
            put("name", name)
        })
    })
}

private fun deterministicId(input: String): String {
    return UUID.nameUUIDFromBytes(input.toByteArray(StandardCharsets.UTF_8)).toString()
}
