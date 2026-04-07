package tree

import http.encodeQueryFormComponent
import http.mergeUrlWithParams
import http.parseHeadersForSend
import http.splitUrlQueryForParamsEditor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val importJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun encodeJsonObjectToString(obj: JsonObject): String =
    importJson.encodeToString(JsonElement.serializer(), obj)

/**
 * 将 Postman Collection v2.0 / v2.1 JSON 解析为 [PortableCollection]，供 [db.CollectionRepository.importAsNewCollection] 落库。
 * 与 [portableCollectionToPostmanV21Json] 导出的结构对称；对 Insomnia 等工具导出的兼容 JSON 尽量宽松解析。
 *
 * @throws IllegalArgumentException 根对象或 info 不合法时
 */
fun parsePostmanCollectionJsonToPortable(jsonText: String): PortableCollection {
    val root = try {
        importJson.parseToJsonElement(jsonText).jsonObject
    } catch (e: Exception) {
        throw IllegalArgumentException("JSON 解析失败: ${e.message}")
    }
    val info = root["info"]?.jsonObject
        ?: throw IllegalArgumentException("缺少 Postman 集合字段 info")
    val name = info["name"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: throw IllegalArgumentException("缺少 info.name")
    val schema = info["schema"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (schema.isNotEmpty()) {
        val s = schema.lowercase()
        if ("openapi" in s || "swagger" in s) {
            throw IllegalArgumentException("请选择 Postman Collection JSON（当前文件像是 OpenAPI）")
        }
    }

    val collectionMeta = buildJsonObject {
        extractDescriptionString(info["description"])?.let { put("description", JsonPrimitive(it)) }
        info["_postman_id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("_postman_id", JsonPrimitive(it))
        }
        root["variable"]?.let { v ->
            if (v is JsonArray && v.isNotEmpty()) put("variable", v)
        }
    }
    val collectionMetaJson = encodeJsonObjectToString(collectionMeta)

    val auth = root["auth"]?.let { parseAuthOrNull(it) }
    val items = root["item"]?.jsonArray ?: JsonArray(emptyList())
    val (folders, rootRequests) = parseItemList(items)

    return PortableCollection(
        name = name,
        collectionMetaJson = collectionMetaJson,
        auth = auth,
        folders = folders,
        rootRequests = rootRequests,
    )
}

private fun parseItemList(items: JsonArray): Pair<List<PortableFolder>, List<PortableRequest>> {
    val folders = mutableListOf<PortableFolder>()
    val requests = mutableListOf<PortableRequest>()
    items.forEachIndexed { index, el ->
        val obj = el.jsonObject
        if (obj.containsKey("request")) {
            requests += parseRequestItem(obj, index)
        } else {
            folders += parseFolderItem(obj, index)
        }
    }
    return folders to requests
}

private fun parseFolderItem(obj: JsonObject, sortOrder: Int): PortableFolder {
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "(文件夹)" }
    val childItems = obj["item"]?.jsonArray ?: JsonArray(emptyList())
    val (subFolders, subReqs) = parseItemList(childItems)
    val metaJson = folderMetaJson(obj)
    val auth = obj["auth"]?.let { parseAuthOrNull(it) }
    return PortableFolder(
        name = name,
        sortOrder = sortOrder,
        metaJson = metaJson,
        auth = auth,
        folders = subFolders,
        requests = subReqs,
    )
}

private fun folderMetaJson(obj: JsonObject): String {
    val desc = extractDescriptionString(obj["description"]) ?: return "{}"
    return encodeJsonObjectToString(buildJsonObject { put("description", JsonPrimitive(desc)) })
}

private fun parseRequestItem(obj: JsonObject, sortOrder: Int): PortableRequest {
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "(请求)" }
    val req = obj["request"]?.jsonObject
        ?: throw IllegalArgumentException("请求「$name」缺少 request 对象")
    val method = req["method"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase() ?: "GET"
    val (url, paramsText) = parsePostmanUrl(req["url"])
    val headersText = postmanHeadersToText(req["header"]?.jsonArray)
    val bodyText = postmanBodyToText(req["body"]?.jsonObject)
    val metaJson = requestMetaJson(obj)
    val auth = obj["auth"]?.let { parseAuthOrNull(it) }
    return PortableRequest(
        name = name,
        method = method,
        url = url,
        headersText = headersText,
        paramsText = paramsText,
        bodyText = bodyText,
        sortOrder = sortOrder,
        metaJson = metaJson,
        auth = auth,
    )
}

private fun requestMetaJson(obj: JsonObject): String {
    val desc = extractDescriptionString(obj["description"]) ?: return "{}"
    return encodeJsonObjectToString(buildJsonObject { put("description", JsonPrimitive(desc)) })
}

private fun extractDescriptionString(el: JsonElement?): String? {
    if (el == null) return null
    return when (el) {
        is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonObject -> {
            el["content"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: el["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
        else -> null
    }
}

private fun parseAuthOrNull(el: JsonElement): PostmanAuth? =
    try {
        importJson.decodeFromJsonElement(PostmanAuth.serializer(), el)
    } catch (_: Exception) {
        null
    }

private fun postmanHeadersToText(arr: JsonArray?): String {
    if (arr == null) return ""
    val lines = mutableListOf<String>()
    for (h in arr) {
        val ho = h.jsonObject
        val key = ho["key"]?.jsonPrimitive?.contentOrNull?.trim() ?: continue
        val value = headerValueToString(ho["value"])
        val disabled = ho["disabled"]?.jsonPrimitive?.booleanOrNull == true
        val line = "$key: $value"
        lines += if (disabled) "! $line" else line
    }
    return lines.joinToString("\n")
}

private fun headerValueToString(v: JsonElement?): String =
    when (v) {
        null -> ""
        is JsonPrimitive -> v.contentOrNull ?: ""
        else -> v.toString().trim()
    }

private fun postmanBodyToText(bodyObj: JsonObject?): String {
    if (bodyObj == null) return ""
    val mode = bodyObj["mode"]?.jsonPrimitive?.contentOrNull ?: "none"
    return when (mode) {
        "raw" -> rawBodyToString(bodyObj["raw"])
        "urlencoded" -> urlencodedBodyToString(bodyObj["urlencoded"]?.jsonArray)
        "graphql" -> bodyObj["graphql"]?.jsonObject?.get("query")?.jsonPrimitive?.contentOrNull ?: ""
        "formdata", "file" -> ""
        else -> ""
    }
}

private fun rawBodyToString(raw: JsonElement?): String =
    when (raw) {
        null -> ""
        is JsonPrimitive -> raw.contentOrNull ?: ""
        else -> raw.toString()
    }

private fun urlencodedBodyToString(arr: JsonArray?): String {
    if (arr == null) return ""
    val pairs = mutableListOf<Pair<String, String>>()
    for (e in arr) {
        val eo = e.jsonObject
        val key = eo["key"]?.jsonPrimitive?.contentOrNull ?: continue
        if (eo["disabled"]?.jsonPrimitive?.booleanOrNull == true) continue
        val value = eo["value"]?.jsonPrimitive?.contentOrNull ?: ""
        pairs += key to value
    }
    if (pairs.isEmpty()) return ""
    return pairs.joinToString("&") { (k, v) ->
        "${encodeQueryFormComponent(k)}=${encodeQueryFormComponent(v)}"
    }
}

private fun parsePostmanUrl(urlEl: JsonElement?): Pair<String, String> {
    if (urlEl == null) return "" to ""
    if (urlEl is JsonPrimitive) {
        val raw = urlEl.contentOrNull?.trim() ?: return "" to ""
        if (raw.isEmpty()) return "" to ""
        return splitUrlQueryForParamsEditor(raw)
    }
    val uo = urlEl.jsonObject
    val raw = uo["raw"]?.jsonPrimitive?.contentOrNull?.trim()
    if (!raw.isNullOrEmpty()) {
        return splitUrlQueryForParamsEditor(raw)
    }
    val protocol = uo["protocol"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
    val hostParts = hostElementToStrings(uo["host"])
    val pathParts = pathElementToStrings(uo["path"])
    val portStr = uo["port"]?.jsonPrimitive?.contentOrNull?.trim()
    val hostStr = hostParts.joinToString(".")
    val authority = when {
        hostStr.isEmpty() -> ""
        portStr.isNullOrBlank() -> hostStr
        else -> "$hostStr:$portStr"
    }
    val pathStr = pathParts.joinToString("/")
    val baseNoScheme = when {
        authority.isNotEmpty() && pathStr.isNotEmpty() -> "$authority/$pathStr"
        authority.isNotEmpty() -> authority
        pathStr.isNotEmpty() -> if (pathStr.startsWith("/")) pathStr else "/$pathStr"
        else -> ""
    }
    val base = when {
        baseNoScheme.isEmpty() -> ""
        protocol.isBlank() -> baseNoScheme
        else -> "$protocol://$baseNoScheme"
    }
    val queryText = queryArrayToParamsText(uo["query"]?.jsonArray)
    val withQuery = when {
        base.isBlank() && queryText.isBlank() -> ""
        base.isBlank() -> mergeUrlWithParams("", parseHeadersForSend(queryText))
        queryText.isBlank() -> base
        else -> mergeUrlWithParams(base, parseHeadersForSend(queryText))
    }
    val hash = uo["hash"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val full = if (hash.isNullOrBlank()) withQuery else "$withQuery#$hash"
    return splitUrlQueryForParamsEditor(full)
}

private fun hostElementToStrings(el: JsonElement?): List<String> {
    if (el == null) return emptyList()
    return when (el) {
        is JsonPrimitive -> listOf(el.contentOrNull ?: "")
        is JsonArray -> el.mapNotNull { elem -> (elem as? JsonPrimitive)?.content }
        else -> emptyList()
    }
}

private fun pathElementToStrings(el: JsonElement?): List<String> {
    if (el == null || el !is JsonArray) return emptyList()
    return el.map { seg ->
        when (seg) {
            is JsonPrimitive -> seg.contentOrNull ?: ""
            else -> seg.toString().trim('"')
        }
    }
}

private fun queryArrayToParamsText(arr: JsonArray?): String {
    if (arr == null) return ""
    val lines = mutableListOf<String>()
    for (q in arr) {
        val qo = q.jsonObject
        val key = qo["key"]?.jsonPrimitive?.contentOrNull ?: continue
        val value = qo["value"]?.jsonPrimitive?.contentOrNull ?: ""
        val disabled = qo["disabled"]?.jsonPrimitive?.booleanOrNull == true
        val line = "$key: $value"
        lines += if (disabled) "! $line" else line
    }
    return lines.joinToString("\n")
}
