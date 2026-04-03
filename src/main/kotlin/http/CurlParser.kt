package http

private val HTTP_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

data class CurlRequest(
    val method: String,
    val url: String,
    val headers: List<String>,
    val body: String
)

fun parseCurlCommand(curlCommand: String): CurlRequest {
    val command = curlCommand.replace("\\\n", " ").trim()
    val tokens = tokenizeShellCommand(command)
    if (tokens.isEmpty() || tokens.first() != "curl") {
        throw IllegalArgumentException("剪贴板内容不是 sh 风格的 curl 命令")
    }

    var method = "GET"
    var url = ""
    val headers = mutableListOf<String>()
    val bodyParts = mutableListOf<String>()
    var hasExplicitMethod = false

    var i = 1
    while (i < tokens.size) {
        when (val token = tokens[i]) {
            "-X", "--request" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少请求方法")
                method = next.uppercase()
                hasExplicitMethod = true
                i += 2
            }
            "-H", "--header" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少 Header 值")
                headers += next
                i += 2
            }
            "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少 Body 值")
                bodyParts += next
                i += 2
            }
            "--url" -> {
                val next = tokens.getOrNull(i + 1) ?: throw IllegalArgumentException("curl 缺少 URL")
                url = next
                i += 2
            }
            else -> {
                if (token.startsWith("http://") || token.startsWith("https://")) {
                    url = token
                } else if (token.uppercase() in HTTP_METHODS && !hasExplicitMethod) {
                    method = token.uppercase()
                }
                i += 1
            }
        }
    }

    if (url.isBlank()) throw IllegalArgumentException("未解析到 URL")
    if (!hasExplicitMethod && bodyParts.isNotEmpty()) {
        method = "POST"
    }

    return CurlRequest(
        method = method,
        url = url,
        headers = headers,
        body = bodyParts.joinToString("&")
    )
}

private fun tokenizeShellCommand(command: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false

    for (ch in command) {
        when {
            escaped -> {
                current.append(ch)
                escaped = false
            }
            ch == '\\' && !inSingleQuote -> escaped = true
            ch == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
            ch == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
            ch.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.setLength(0)
                }
            }
            else -> current.append(ch)
        }
    }

    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}
