package http

/** 生成可在 shell 中执行的 cURL 单行命令（单引号转义）。 */
fun requestToCurlCommand(method: String, url: String, headersText: String, bodyText: String): String {
    val m = method.trim().uppercase().ifEmpty { "GET" }
    val u = url.trim()
    val sb = StringBuilder("curl")
    if (m != "GET") {
        sb.append(" -X ").append(shellSingleQuote(m))
    }
    sb.append(" ").append(shellSingleQuote(u))
    for (raw in headersText.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val colon = line.indexOf(':')
        if (colon <= 0) continue
        val name = line.substring(0, colon).trim()
        val value = line.substring(colon + 1).trim()
        if (name.isEmpty()) continue
        sb.append(" -H ").append(shellSingleQuote("$name: $value"))
    }
    if (bodyText.isNotBlank() && m in CURL_BODY_METHODS) {
        sb.append(" --data ").append(shellSingleQuote(bodyText))
    }
    return sb.toString()
}

private val CURL_BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

private fun shellSingleQuote(s: String): String =
    "'${s.replace("'", "'\"'\"'")}'"
