package app.log

import db.AppPaths
import org.tinylog.Logger as Tinylog
import org.tinylog.configuration.Configuration

object Logger {
    private const val MAX_MSG_LENGTH = 200

    inline fun info(tag: String, crossinline block: () -> String) {
        if (isEnabled) Tinylog.info { "[$tag] ${truncate(block())}" }
    }

    inline fun error(tag: String, throwable: Throwable? = null, crossinline block: () -> String) {
        if (isEnabled) {
            val msg = truncate(block())
            if (throwable != null) Tinylog.error(throwable) { "[$tag] $msg" }
            else Tinylog.error { "[$tag] $msg" }
        }
    }

    inline fun warn(tag: String, throwable: Throwable? = null, crossinline block: () -> String) {
        if (isEnabled) {
            val msg = truncate(block())
            if (throwable != null) Tinylog.warn(throwable) { "[$tag] $msg" }
            else Tinylog.warn { "[$tag] $msg" }
        }
    }

    inline fun debug(tag: String, crossinline block: () -> String) {
        if (isEnabled) Tinylog.debug { "[$tag] ${truncate(block())}" }
    }

    val isEnabled: Boolean
        get() = System.getProperty("api-x.log") != "false"

    @PublishedApi
    internal fun truncate(s: String): String {
        if (s.length <= MAX_MSG_LENGTH) return s
        return s.take(MAX_MSG_LENGTH - 3) + "..."
    }

    fun configure() {
        val logDir = AppPaths.logDirectory()
        val filePattern = logDir.resolve("{date: yyyy-MM-dd}/api-x_{count}.log").toString()
        Configuration.set("writer.file", filePattern)
        Tinylog.info { "[APP] Api-X log initialized, dir: $logDir" }
    }
}
