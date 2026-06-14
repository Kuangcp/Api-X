package app.core

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object GlobalExceptionHandler {

    private val errorLog = mutableListOf<String>()

    @Volatile
    var lastError: Throwable? = null
        private set

    var onErrorCaptured: ((Throwable) -> Unit)? = null

    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            lastError = throwable
            val crashInfo = buildCrashReport(thread, throwable)
            synchronized(errorLog) {
                errorLog.add(crashInfo)
                if (errorLog.size > 50) errorLog.removeAt(0)
            }
            System.err.println(crashInfo)
            onErrorCaptured?.invoke(throwable)
        }
    }

    fun getRecentErrors(): List<String> = synchronized(errorLog) {
        errorLog.toList()
    }

    fun clearError() {
        lastError = null
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        return buildString {
            appendLine("========== UNCAUGHT EXCEPTION ==========")
            appendLine("Time: $timestamp")
            appendLine("Thread: ${thread.name}")
            appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("Stack Trace:")
            appendLine(sw.toString())
            appendLine("=========================================")
        }
    }
}
