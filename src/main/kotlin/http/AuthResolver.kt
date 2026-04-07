package http

import tree.PostmanAuth
import java.util.Base64
import http.applyEnvironmentVariables
import tree.findValue

/**
 * 将 PostmanAuth 转换为 HTTP 头部。
 * 暂时支持最常用的 Basic, Bearer, ApiKey。
 */
fun resolveAuthToHeaders(auth: PostmanAuth?, varMap: Map<String, String>): List<Pair<String, String>> {
    if (auth == null || auth.type == "noauth") return emptyList()

    return when (auth.type) {
        "basic" -> {
            val username = applyEnvironmentVariables(auth.basic.findValue("username") ?: "", varMap)
            val password = applyEnvironmentVariables(auth.basic.findValue("password") ?: "", varMap)
            val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            listOf("Authorization" to "Basic $encoded")
        }
        "bearer" -> {
            val token = applyEnvironmentVariables(auth.bearer.findValue("token") ?: "", varMap)
            listOf("Authorization" to "Bearer $token")
        }
        "apikey" -> {
            val key = applyEnvironmentVariables(auth.apikey.findValue("key") ?: "", varMap)
            val value = applyEnvironmentVariables(auth.apikey.findValue("value") ?: "", varMap)
            val addTo = auth.apikey.findValue("in") ?: "header"
            if (addTo == "header") {
                listOf(key to value)
            } else {
                // query params 处理逻辑通常在 URL 拼接处，这里仅返回 header
                emptyList()
            }
        }
        else -> emptyList() // 其他类型暂不支持自动转换
    }
}
