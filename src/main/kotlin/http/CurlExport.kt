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
    for ((name, value) in parseHeaders(headersText)) {
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
