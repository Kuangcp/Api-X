package http

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val UTF_8 = StandardCharsets.UTF_8

fun encodeQueryFormComponent(s: String): String = URLEncoder.encode(s, UTF_8)

/**
 * 将 Params 标签页中启用的键值对追加到 URL 查询串（UTF-8 百分号编码）。
 * 保留 URL 中已有的 `?` 查询，用 `&` 拼接；`#fragment` 始终在末尾。
 */
fun mergeUrlWithParams(urlString: String, paramPairs: List<Pair<String, String>>): String {
    if (paramPairs.isEmpty()) return urlString.trim()
    val encoded = paramPairs.joinToString("&") { (k, v) ->
        "${encodeQueryFormComponent(k)}=${encodeQueryFormComponent(v)}"
    }
    val trimmed = urlString.trim()
    if (trimmed.isEmpty()) return "?$encoded"

    val hashIdx = trimmed.indexOf('#')
    val beforeHash = if (hashIdx >= 0) trimmed.substring(0, hashIdx) else trimmed
    val fragment = if (hashIdx >= 0) trimmed.substring(hashIdx) else ""

    val qIdx = beforeHash.indexOf('?')
    val pathPart = if (qIdx >= 0) beforeHash.substring(0, qIdx) else beforeHash
    val existingQuery = if (qIdx >= 0) beforeHash.substring(qIdx + 1) else ""

    val queryString = when {
        existingQuery.isBlank() -> encoded
        else -> "$existingQuery&$encoded"
    }
    return "$pathPart?$queryString$fragment"
}

/**
 * 从 URL 拆出不含 `?query` 的基础 URL（保留 `#fragment`），并把查询解析为 Params 文本（每行 `key: value`，与 Headers 相同）。
 */
fun splitUrlQueryForParamsEditor(urlString: String): Pair<String, String> {
    val trimmed = urlString.trim()
    val hashIdx = trimmed.indexOf('#')
    val beforeHash = if (hashIdx >= 0) trimmed.substring(0, hashIdx) else trimmed
    val fragment = if (hashIdx >= 0) trimmed.substring(hashIdx) else ""

    val qIdx = beforeHash.indexOf('?')
    if (qIdx < 0) return trimmed to ""

    val pathPart = beforeHash.substring(0, qIdx)
    val query = beforeHash.substring(qIdx + 1)
    if (query.isBlank()) return "$pathPart$fragment" to ""

    val lines = mutableListOf<String>()
    for (part in query.split('&')) {
        if (part.isEmpty()) continue
        val eq = part.indexOf('=')
        val rawName = if (eq < 0) part else part.substring(0, eq)
        val rawVal = if (eq < 0) "" else part.substring(eq + 1)
        val name = URLDecoder.decode(rawName.replace('+', ' '), UTF_8)
        val value = URLDecoder.decode(rawVal.replace('+', ' '), UTF_8)
        if (name.isNotBlank()) lines += "$name: $value"
    }
    return "$pathPart$fragment" to lines.joinToString("\n")
}

/**
 * 发起请求时：若 URL 不以 `http://` 或 `https://` 开头（忽略大小写），则前置 `http://`。
 * 空白字符串原样返回。
 */
fun ensureDefaultHttpScheme(url: String): String {
    val u = url.trim()
    if (u.isEmpty()) return u
    val low = u.lowercase()
    if (low.startsWith("http://") || low.startsWith("https://")) return u
    return "http://$u"
}
