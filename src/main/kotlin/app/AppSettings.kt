package app

import db.AppPaths
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

/** 用户偏好：界面字体与 HTTP 代理。持久化到应用数据目录。 */
data class AppSettings(
    val fontFamilyName: String = "",
    val fontSizeSp: Float = 13f,
    /** 中间 Request / Response 编辑区正文字号（sp），与全局界面字体独立。 */
    val requestResponseFontSizeSp: Float = 13f,
    /** 主窗口背景色，如 `#282923`；空则跟随主题默认。 */
    val backgroundHex: String = "",
    val httpProxyUrl: String = "",
    val httpsProxyUrl: String = "",
    /** 每行一条正则，匹配请求主机名则直连（不走代理）。 */
    val bypassRegexLines: String = "",
) {
    companion object {
        private const val KEY_FONT_FAMILY = "ui.fontFamily"
        private const val KEY_FONT_SIZE = "ui.fontSizeSp"
        private const val KEY_REQ_RESP_FONT = "ui.requestResponseFontSizeSp"
        private const val KEY_BACKGROUND = "ui.backgroundHex"
        private const val KEY_HTTP_PROXY = "proxy.http"
        private const val KEY_HTTPS_PROXY = "proxy.https"
        private const val KEY_BYPASS = "proxy.bypassRegex"

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
                    httpProxyUrl = props.getProperty(KEY_HTTP_PROXY, "").trim(),
                    httpsProxyUrl = props.getProperty(KEY_HTTPS_PROXY, "").trim(),
                    bypassRegexLines = props.getProperty(KEY_BYPASS, "").trim(),
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
                props.setProperty(KEY_HTTP_PROXY, settings.httpProxyUrl)
                props.setProperty(KEY_HTTPS_PROXY, settings.httpsProxyUrl)
                props.setProperty(KEY_BYPASS, settings.bypassRegexLines)
                Files.newOutputStream(path()).use { out ->
                    props.store(out, "api-x app settings")
                }
            }
        }
    }
}

/** 内存中的当前设置，供 UI 与 [http.ApiXProxySelector] 读取。 */
object AppSettingsStore {
    private val ref = AtomicReference(AppSettings.load())

    fun snapshot(): AppSettings = ref.get()

    fun replace(settings: AppSettings) {
        ref.set(settings)
        AppSettings.save(settings)
    }
}
