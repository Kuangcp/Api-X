package tree

import http.bodyWirePayloadForHttp
import http.mergeUrlWithParams
import http.parseHeaderLine
import http.parseHeadersForSend
import http.splitHeadersForEditor
import http.splitUrlQueryForParamsEditor
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Postman Collection v2.1.0 schema URL（Insomnia / Postman 均识别）。 */
const val POSTMAN_COLLECTION_SCHEMA_V21 = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"

private val jsonLenient = Json { ignoreUnknownKeys = true }
private val jsonPretty = Json { ignoreUnknownKeys = true; prettyPrint = true }

/**
 * 将 [PortableCollection] 序列化为 Postman Collection v2.1 兼容的 JSON（带缩进）。
 * 字段覆盖：嵌套文件夹、根请求、method / url（含 Params 合并为 query）、headers（含禁用行）、raw body、各层 auth、集合级 variable / description / _postman_id（来自 meta_json）。
 */
fun portableCollectionToPostmanV21Json(portable: PortableCollection): String {
    val root = buildPostmanCollectionRoot(portable)
    return jsonPretty.encodeToString(JsonElement.serializer(), root)
}

private fun buildPostmanCollectionRoot(portable: PortableCollection): JsonObject {
    val meta = parseMetaObject(portable.collectionMetaJson)
    val info = buildJsonObject {
        put("name", portable.name)
        put("schema", POSTMAN_COLLECTION_SCHEMA_V21)
        val id = meta.stringOrNull("_postman_id") ?: UUID.randomUUID().toString()
        put("_postman_id", id)
        meta.stringOrNull("description")?.takeIf { it.isNotBlank() }?.let { put("description", it) }
        portable.id?.takeIf { it.isNotBlank() }?.let { put("_api_x_id", it) }
    }
    return buildJsonObject {
        put("info", info)
        authToPostmanJson(portable.auth)?.let { put("auth", it) }
        meta["variable"]?.let { v ->
            if (v is JsonArray && v.isNotEmpty()) put("variable", v)
        }
        put("item", buildJsonArray {
            mergePortableRootItems(portable).forEach { add(it) }
        })
    }
}

private fun mergePortableRootItems(p: PortableCollection): List<JsonObject> {
    data class Tagged(val order: Int, val name: String, val obj: JsonObject)
    val list = mutableListOf<Tagged>()
    p.folders.forEach { f -> list += Tagged(f.sortOrder, f.name, folderToPostmanItem(f)) }
    p.rootRequests.forEach { r -> list += Tagged(r.sortOrder, r.name, requestToPostmanItem(r)) }
    list.sortWith(compareBy({ it.order }, { it.name }))
    return list.map { it.obj }
}

private fun mergePortableFolderChildren(f: PortableFolder): List<JsonObject> {
    data class Tagged(val order: Int, val name: String, val obj: JsonObject)
    val list = mutableListOf<Tagged>()
    f.folders.forEach { c -> list += Tagged(c.sortOrder, c.name, folderToPostmanItem(c)) }
    f.requests.forEach { r -> list += Tagged(r.sortOrder, r.name, requestToPostmanItem(r)) }
    list.sortWith(compareBy({ it.order }, { it.name }))
    return list.map { it.obj }
}

private fun folderToPostmanItem(f: PortableFolder): JsonObject = buildJsonObject {
    f.id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
    put("name", f.name)
    metaStringDescription(f.metaJson)?.let { put("description", it) }
    authToPostmanJson(f.auth)?.let { put("auth", it) }
    put("item", buildJsonArray {
        mergePortableFolderChildren(f).forEach { add(it) }
    })
}

private fun requestToPostmanItem(r: PortableRequest): JsonObject = buildJsonObject {
    r.id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
    put("name", r.name)
    metaStringDescription(r.metaJson)?.let { put("description", it) }
    authToPostmanJson(r.auth)?.let { put("auth", it) }
    put("request", buildPostmanRequestObject(r))
    put("response", buildJsonArray { })
}

private fun buildPostmanRequestObject(r: PortableRequest): JsonObject = buildJsonObject {
    put("method", r.method.trim().uppercase())
    put("header", headersToPostmanArray(r.headersText))
    put("body", bodyToPostmanObject(r.headersText, r.bodyText))
    put("url", urlToPostmanObject(r.url, r.paramsText))
}

private fun headersToPostmanArray(headersText: String): JsonArray {
    val (valid, _) = splitHeadersForEditor(headersText)
    return buildJsonArray {
        for ((k, v, enabled) in valid) {
            addJsonObject {
                put("key", k)
                put("value", v)
                put("type", "text")
                put("disabled", !enabled)
            }
        }
    }
}

private fun bodyToPostmanObject(headersText: String, bodyText: String): JsonObject {
    if (bodyText.isBlank()) {
        return buildJsonObject { put("mode", "none") }
    }
    val ct = contentTypeFromHeaders(headersText)
    val language = when {
        ct.contains("json", ignoreCase = true) -> "json"
        ct.contains("xml", ignoreCase = true) -> "xml"
        ct.contains("html", ignoreCase = true) -> "html"
        else -> "text"
    }
    val rawForPostman = bodyWirePayloadForHttp(bodyText, headersText)
    return buildJsonObject {
        put("mode", "raw")
        put("raw", rawForPostman)
        put(
            "options",
            buildJsonObject {
                put("raw", buildJsonObject { put("language", language) })
            },
        )
    }
}

private fun contentTypeFromHeaders(headersText: String): String {
    val (valid, _) = splitHeadersForEditor(headersText)
    return valid.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second ?: ""
}

private fun urlToPostmanObject(urlRaw: String, paramsText: String): JsonObject {
    val merged = mergeUrlWithParams(urlRaw.trim(), parseHeadersForSend(paramsText))
    val (baseWithFrag, queryBlockText) = splitUrlQueryForParamsEditor(merged)
    val queryArr = buildJsonArray {
        for (line in queryBlockText.lines()) {
            val l = line.trim().trimEnd('\r')
            if (l.isEmpty()) continue
            parseHeaderLine(l)?.let { (k, v) ->
                addJsonObject {
                    put("key", k)
                    put("value", v)
                    put("disabled", false)
                }
            }
        }
    }
    val hashIdx = baseWithFrag.indexOf('#')
    val beforeHash = if (hashIdx >= 0) baseWithFrag.substring(0, hashIdx) else baseWithFrag
    val fragment = if (hashIdx >= 0) baseWithFrag.substring(hashIdx + 1) else ""

    return buildJsonObject {
        put("raw", merged)
        if (queryArr.isNotEmpty()) put("query", queryArr)
        if (fragment.isNotEmpty()) put("hash", fragment)
        tryParseHttpUri(beforeHash)?.let { u ->
            put("protocol", u.protocol)
            put("host", buildJsonArray { u.hostSegments.forEach { add(JsonPrimitive(it)) } })
            if (u.pathSegments.isNotEmpty()) {
                put("path", buildJsonArray { u.pathSegments.forEach { add(JsonPrimitive(it)) } })
            }
            u.portString?.let { put("port", it) }
        }
    }
}

private data class ParsedHttpUri(
    val protocol: String,
    val hostSegments: List<String>,
    val pathSegments: List<String>,
    val portString: String?,
)

private fun tryParseHttpUri(baseNoQuery: String): ParsedHttpUri? {
    val s = baseNoQuery.trim()
    if (s.isEmpty()) return null
    return try {
        val uri = URI(s)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val hostSegments = host.split('.').filter { it.isNotEmpty() }
        val rawPath = uri.path ?: ""
        val pathSegments = rawPath.trim('/').split('/').filter { it.isNotEmpty() }
        val port = uri.port
        val defaultPort = if (scheme == "https") 443 else 80
        val portStr = if (port > 0 && port != defaultPort) port.toString() else null
        ParsedHttpUri(scheme, hostSegments, pathSegments, portStr)
    } catch (_: Exception) {
        null
    }
}

private fun authToPostmanJson(auth: PostmanAuth?): JsonObject? {
    if (auth == null) return null
    if (auth.type == "inherit") return null
    return jsonLenient.encodeToJsonElement(PostmanAuth.serializer(), auth).jsonObject
}

private fun parseMetaObject(metaJson: String): JsonObject =
    try {
        jsonLenient.parseToJsonElement(metaJson.ifBlank { "{}" }).jsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun metaStringDescription(metaJson: String): String? {
    val meta = parseMetaObject(metaJson)
    return meta.stringOrNull("description")?.takeIf { it.isNotBlank() }
}
