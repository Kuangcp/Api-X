package http

/** Body 编辑区类型：与 [contentTypeValueForBodyKind] 对应。 */
enum class BodyContentKind {
    FormUrlEncoded,
    Json,
    Xml,
}

fun contentTypeValueForBodyKind(kind: BodyContentKind): String = when (kind) {
    BodyContentKind.FormUrlEncoded -> "application/x-www-form-urlencoded"
    BodyContentKind.Json -> "application/json"
    BodyContentKind.Xml -> "application/xml"
}

/**
 * 根据现有 `Content-Type` 推断 Body 类型；无法识别时默认为表单。
 */
fun inferBodyKindFromHeaders(headersText: String): BodyContentKind {
    val (valid, _) = splitHeadersForEditor(headersText)
    val ct = valid.firstOrNull { it.first.equals("Content-Type", ignoreCase = true) }?.second?.trim().orEmpty()
    if (ct.isEmpty()) return BodyContentKind.FormUrlEncoded
    val lower = ct.lowercase()
    return when {
        lower.contains("json") -> BodyContentKind.Json
        lower.contains("xml") -> BodyContentKind.Xml
        else -> BodyContentKind.FormUrlEncoded
    }
}

/** 更新或插入 `Content-Type` 行，保留该行原有的启用/禁用状态。 */
fun upsertContentTypeHeader(headersText: String, contentTypeValue: String): String {
    val (valid, orphan) = splitHeadersForEditor(headersText)
    val rows = valid.toMutableList()
    val idx = rows.indexOfFirst { it.first.equals("Content-Type", ignoreCase = true) }
    if (idx >= 0) {
        val (_, _, enabled) = rows[idx]
        rows[idx] = Triple("Content-Type", contentTypeValue, enabled)
    } else {
        rows.add(0, Triple("Content-Type", contentTypeValue, true))
    }
    return joinHeadersEditor(rows, orphan)
}
