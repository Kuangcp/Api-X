package app.log

import db.AppPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.text.StringBuilder

object Logger {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    @Volatile
    private var writer: LogWriter? = null

    private val lock = Any()

    private fun currentWriter(): LogWriter = synchronized(lock) {
        val today = dateFormatter.format(Instant.now())
        val existing = writer
        if (existing != null && existing.date == today && !existing.rotated()) {
            return@synchronized existing
        }
        val w = LogWriter(today)
        writer = w
        w
    }

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null, source: StackTraceElement? = null) {
        val ts = timeFormatter.format(Instant.now())
        val sb = StringBuilder()
            .append(ts).append(" ")
            .append("[").append(level).append("] ")
            .append("[").append(tag).append("] ")
        if (source != null) {
            val shortFile = source.fileName ?: "?"
            sb.append("(").append(shortFile).append(":").append(source.lineNumber).append(") ")
        }
        sb.append(truncate(message, 200)).append("\n")
        throwable?.let { t ->
            sb.append(t.stackTraceToString()).append("\n")
        }
        try {
            currentWriter().append(sb.toString())
        } catch (_: Exception) {
        }
    }

    inline fun debug(tag: String, block: () -> String) {
        if (isEnabled) {
            val source = Throwable().stackTrace.getOrNull(0)
            log("D", tag, block(), source = source)
        }
    }

    inline fun info(tag: String, block: () -> String) {
        if (isEnabled) {
            val source = Throwable().stackTrace.getOrNull(0)
            log("I", tag, block(), source = source)
        }
    }

    inline fun warn(tag: String, throwable: Throwable? = null, block: () -> String) {
        if (isEnabled) {
            val source = Throwable().stackTrace.getOrNull(0)
            log("W", tag, block(), throwable, source)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun error(tag: String, throwable: Throwable? = null, block: () -> String) {
        if (isEnabled) {
            val source = Throwable().stackTrace.getOrNull(0)
            log("E", tag, block(), throwable, source)
        }
    }

    private fun truncate(s: String, maxLen: Int): String {
        if (s.length <= maxLen) return s
        return s.take(maxLen - 3) + "..."
    }

    private class LogWriter(val date: String) {
        private val dir: Path = AppPaths.logDirectory().resolve(date)
        private var currentFile: Path? = null
        private var currentIndex = 0
        private var currentSize = 0L
        private val maxSize = 10L * 1024 * 1024

        init {
            Files.createDirectories(dir)
            openNextFile()
        }

        fun rotated(): Boolean {
            return currentSize >= maxSize
        }

        @Synchronized
        fun append(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (currentSize + bytes.size > maxSize) {
                openNextFile()
            }
            val file = currentFile ?: return
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            currentSize += bytes.size
        }

        private fun openNextFile() {
            do {
                val f = dir.resolve("${date}_${currentIndex}.log")
                if (!Files.exists(f) || Files.size(f) < maxSize) {
                    currentFile = f
                    currentSize = if (Files.exists(f)) Files.size(f) else 0L
                    return
                }
                currentIndex++
            } while (currentIndex < 1000)
        }
    }

    val isEnabled: Boolean
        get() = System.getProperty("api-x.log") != "false"
}
