package app.settings

import db.AppPaths
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AppSettings(
    val fontFamilyName: String = "",
    val fontSizeSp: Float = 13f,
    val requestResponseFontSizeSp: Float = 13f,
    val backgroundHex: String = "",
    val httpConnectTimeoutMillis: Long = 10_000L,
    val httpReadTimeoutMillis: Long = 6000L,
    val httpRequestTimeoutMillis: Long = 10000L,
    val httpProxyUrl: String = "",
    val httpsProxyUrl: String = "",
    val bypassRegexLines: String = "",
    val httpProtocolVersion: String = "",
    val responseSseTextRulePaths: List<String> = emptyList(),
) {
    companion object {
        private const val KEY_FONT_FAMILY = "ui.fontFamily"
        private const val KEY_FONT_SIZE = "ui.fontSizeSp"
        private const val KEY_REQ_RESP_FONT = "ui.requestResponseFontSizeSp"
        private const val KEY_BACKGROUND = "ui.backgroundHex"
        private const val KEY_HTTP_CONNECT_MS = "http.connectTimeoutMillis"
        private const val KEY_HTTP_CONNECT_SEC_LEGACY = "http.connectTimeoutSeconds"
        private const val KEY_HTTP_READ_MS = "http.readTimeoutMillis"
        private const val KEY_HTTP_REQUEST_MS = "http.requestTimeoutMillis"
        private const val KEY_HTTP_PROXY = "proxy.http"
        private const val KEY_HTTPS_PROXY = "proxy.https"
        private const val KEY_BYPASS = "proxy.bypassRegex"
        private const val KEY_HTTP_PROTOCOL = "http.protocolVersion"
        private const val KEY_RESPONSE_SSE_TEXT_RULES = "response.sseTextRulePathsJson"

        private val settingsJson = Json { ignoreUnknownKeys = true }

        private fun path() = AppPaths.dataDirectory().resolve("app-settings.properties")

        fun load(): AppSettings {
            val file = path()
            if (!Files.isRegularFile(file)) return AppSettings()
            return runCatching {
                val props = Properties()
                Files.newInputStream(file).use { props.load(it) }
                AppSettings(
                    fontFamilyName = props.getProperty(KEY_FONT_FAMILY, "").trim(),
                    fontSizeSp = props.getProperty(KEY_FONT_SIZE)?.toFloatOrNull()?.coerceIn(8f, 32f) ?: 13f,
                    requestResponseFontSizeSp = props.getProperty(KEY_REQ_RESP_FONT)?.toFloatOrNull()?.coerceIn(9f, 28f)
                        ?: 13f,
                    backgroundHex = props.getProperty(KEY_BACKGROUND, "").trim(),
                    httpConnectTimeoutMillis = run {
                        props.getProperty(KEY_HTTP_CONNECT_MS)?.toLongOrNull()?.let {
                            it.coerceIn(1L, 86_400_000L)
                        } ?: props.getProperty(KEY_HTTP_CONNECT_SEC_LEGACY)?.toLongOrNull()?.let { legacy ->
                            if (legacy in 1L..86400L) legacy * 1000L else legacy.coerceIn(1L, 86_400_000L)
                        } ?: 10_000L
                    },
                    httpReadTimeoutMillis = props.getProperty(KEY_HTTP_READ_MS)?.toLongOrNull()?.coerceIn(1L, 86_400_000L)
                        ?: 6000L,
                    httpRequestTimeoutMillis = props.getProperty(KEY_HTTP_REQUEST_MS)?.toLongOrNull()
                        ?.coerceIn(1L, 86_400_000L) ?: 10000L,
                    httpProxyUrl = props.getProperty(KEY_HTTP_PROXY, "").trim(),
                    httpsProxyUrl = props.getProperty(KEY_HTTPS_PROXY, "").trim(),
                    bypassRegexLines = props.getProperty(KEY_BYPASS, "").trim(),
                    httpProtocolVersion = props.getProperty(KEY_HTTP_PROTOCOL, "").let {
                        if (it in listOf("", "HTTP_1_1", "HTTP_2")) it else ""
                    },
                    responseSseTextRulePaths = decodeResponseSseTextRulePaths(
                        props.getProperty(KEY_RESPONSE_SSE_TEXT_RULES, ""),
                    ),
                )
            }.getOrElse { AppSettings() }
        }

        fun save(settings: AppSettings) {
            runCatching {
                val props = Properties()
                props.setProperty(KEY_FONT_FAMILY, settings.fontFamilyName)
                props.setProperty(KEY_FONT_SIZE, settings.fontSizeSp.toString())
                props.setProperty(KEY_REQ_RESP_FONT, settings.requestResponseFontSizeSp.toString())
                props.setProperty(KEY_BACKGROUND, settings.backgroundHex)
                props.setProperty(KEY_HTTP_CONNECT_MS, settings.httpConnectTimeoutMillis.toString())
                props.setProperty(KEY_HTTP_READ_MS, settings.httpReadTimeoutMillis.toString())
                props.setProperty(KEY_HTTP_REQUEST_MS, settings.httpRequestTimeoutMillis.toString())
                props.setProperty(KEY_HTTP_PROXY, settings.httpProxyUrl)
                props.setProperty(KEY_HTTPS_PROXY, settings.httpsProxyUrl)
                props.setProperty(KEY_BYPASS, settings.bypassRegexLines)
                props.setProperty(KEY_HTTP_PROTOCOL, settings.httpProtocolVersion)
                props.setProperty(
                    KEY_RESPONSE_SSE_TEXT_RULES,
                    settingsJson.encodeToString(normalizeResponseSseTextRulePaths(settings.responseSseTextRulePaths)),
                )
                Files.newOutputStream(path()).use { out ->
                    props.store(out, "api-x app settings")
                }
            }
        }

        private fun decodeResponseSseTextRulePaths(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                normalizeResponseSseTextRulePaths(settingsJson.decodeFromString<List<String>>(raw))
            }.getOrDefault(emptyList())
        }

        fun normalizeResponseSseTextRulePaths(paths: List<String>): List<String> {
            val seen = LinkedHashSet<String>()
            for (path in paths) {
                val trimmed = path.trim()
                if (isValidResponseSseTextRulePath(trimmed)) seen += trimmed
            }
            return seen.toList()
        }

        fun isValidResponseSseTextRulePath(path: String): Boolean {
            if (path.isBlank()) return false
            return path.split('.').all { segment ->
                val key = if (segment.endsWith("[]")) segment.dropLast(2) else segment
                key.isNotBlank() && key.all { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }
            }
        }
    }
}

class AppSettingsStore {
    private val ref = AtomicReference(AppSettings.load())

    fun snapshot(): AppSettings = ref.get()

    fun replace(settings: AppSettings) {
        ref.set(settings)
        AppSettings.save(settings)
    }
}

object AppSettingsBridge {
    lateinit var store: AppSettingsStore
}