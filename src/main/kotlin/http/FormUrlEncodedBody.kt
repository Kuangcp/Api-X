package http

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 将 `application/x-www-form-urlencoded` 单行（`a=b&c=d`）转为与 Headers 相同的 `Key: value` 多行，供表单编辑。
 */
fun formEditorContentFromWire(wire: String): String {
    val trimmed = wire.trim()
    if (trimmed.isEmpty()) return ""
    val parts = trimmed.split('&').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""
    val rows = mutableListOf<Triple<String, String, Boolean>>()
    for (part in parts) {
        val eq = part.indexOf('=')
        val rawKey = if (eq < 0) part else part.substring(0, eq)
        val rawVal = if (eq < 0) "" else part.substring(eq + 1)
        val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8)
        val value = URLDecoder.decode(rawVal, StandardCharsets.UTF_8)
        rows.add(Triple(key, value, true))
    }
    return joinHeadersEditor(rows, emptyList())
}

/**
 * 将 Headers 风格正文（`splitHeadersForEditor`）编码为 URL 表单串；仅包含已启用且 Key 非空的行。
 */
fun wireBodyFromFormEditorContent(editorText: String): String {
    val (valid, _) = splitHeadersForEditor(editorText)
    val parts = mutableListOf<String>()
    for ((k, v, enabled) in valid) {
        if (!enabled || k.isBlank()) continue
        val ek = URLEncoder.encode(k, StandardCharsets.UTF_8)
        val ev = URLEncoder.encode(v, StandardCharsets.UTF_8)
        parts.add("$ek=$ev")
    }
    return parts.joinToString("&")
}

/** 发起请求或导出 cURL 时：表单类型编码为 wire，其余原样。 */
fun bodyWirePayloadForHttp(bodyText: String, headersText: String): String {
    return when (inferBodyKindFromHeaders(headersText)) {
        BodyContentKind.FormUrlEncoded -> wireBodyFromFormEditorContent(bodyText)
        else -> bodyText
    }
}

/**
 * 从 DB/导入拿到正文后：若为表单且仍是 `a=b&c=d` 旧格式，则转为 `Key: value` 行，便于与 Headers 同款表格编辑。
 */
fun migrateFormBodyToEditorLinesIfNeeded(body: String, headersText: String): String {
    if (inferBodyKindFromHeaders(headersText) != BodyContentKind.FormUrlEncoded) return body
    val t = body.trim()
    if (t.isEmpty()) return body
    val start = t.trimStart()
    if (start.startsWith('{') || start.startsWith('[') || start.startsWith('<')) return body
    if (t.contains('\n')) {
        val first = t.lineSequence().first().trim().trimEnd('\r')
        val lineForParse = if (first.startsWith("! ")) first.removePrefix("! ").trimStart() else first
        if (parseHeaderLine(lineForParse) != null) return body
        return body
    }
    if (parseHeaderLine(t) != null) return body
    if (t.contains('&') || t.contains('=')) {
        return formEditorContentFromWire(t)
    }
    return body
}
