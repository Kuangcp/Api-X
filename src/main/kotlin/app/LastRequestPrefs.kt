package app

import db.AppPaths
import java.nio.file.Files
import java.util.Properties

/** 记住侧栏最后选中的请求 id，下次启动恢复。 */
object LastRequestPrefs {

    private const val KEY = "lastRequestId"

    private fun path() = AppPaths.dataDirectory().resolve("last-request.properties")

    fun load(): String {
        val file = path()
        if (!Files.isRegularFile(file)) return ""
        return runCatching {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            props.getProperty(KEY, "")?.trim() ?: ""
        }.getOrElse { "" }
    }

    fun save(requestId: String) {
        runCatching {
            val props = Properties()
            props.setProperty(KEY, requestId)
            Files.newOutputStream(path()).use { out ->
                props.store(out, "api-x last selected request")
            }
        }
    }

    fun clear() {
        runCatching {
            Files.deleteIfExists(path())
        }
    }
}
