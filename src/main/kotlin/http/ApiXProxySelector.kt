package http

import app.AppSettings
import app.AppSettingsStore
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * 按 [AppSettingsStore] 中的配置选择代理：HTTPS 与 HTTP 可分别配置；
 * 主机名命中 [AppSettings.bypassRegexLines] 中任一行编译出的正则时直连。
 * 未配置代理时使用直连，不采用系统代理环境变量。
 */
object ApiXProxySelector : ProxySelector() {

    private val fallback = getDefault()

    override fun select(uri: URI?): List<Proxy> {
        if (uri == null) return listOf(Proxy.NO_PROXY)
        val host = uri.host
        if (host.isNullOrEmpty()) return listOf(Proxy.NO_PROXY)

        val s = AppSettingsStore.snapshot()
        if (shouldBypass(host, s.bypassRegexLines)) {
            return listOf(Proxy.NO_PROXY)
        }

        val spec = proxySpecForScheme(uri.scheme, s).trim()
        if (spec.isEmpty()) {
            return listOf(Proxy.NO_PROXY)
        }

        val proxy = parseHttpProxy(spec) ?: return listOf(Proxy.NO_PROXY)
        return listOf(proxy)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        fallback.connectFailed(uri, sa, ioe)
    }
}

private fun shouldBypass(host: String, bypassText: String): Boolean {
    for (line in bypassText.lineSequence()) {
        val pattern = line.trim()
        if (pattern.isEmpty()) continue
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: continue
        if (regex.matches(host)) return true
    }
    return false
}

private fun proxySpecForScheme(scheme: String?, settings: AppSettings): String {
    return when (scheme?.lowercase()) {
        "https" -> settings.httpsProxyUrl.ifBlank { settings.httpProxyUrl }
        else -> settings.httpProxyUrl.ifBlank { settings.httpsProxyUrl }
    }
}

private fun parseHttpProxy(spec: String): Proxy? {
    val normalized = if (!spec.contains("://")) "http://$spec" else spec
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val proxyHost = uri.host ?: return null
    val port = when {
        uri.port > 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
    return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyHost, port))
}
