package db

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists

object McpSessionLogStore {
    private fun requestDir(requestId: String): Path =
        AppPaths.requestArtifactsRoot().resolve(sanitizeRequestId(requestId))

    private fun logFile(requestId: String): Path =
        requestDir(requestId).resolve("mcp").resolve("session.log")

    private fun sanitizeRequestId(id: String): String {
        val t = id.trim()
        require(t.isNotEmpty() && !t.contains("..") && t.none { it == '/' || it == '\\' }) {
            "Illegal request id"
        }
        return t
    }

    fun loadLog(requestId: String): List<String> {
        val id = requestId.trim()
        if (id.isEmpty()) return emptyList()
        val file = runCatching { logFile(id) }.getOrNull() ?: return emptyList()
        if (file.notExists() || !Files.isRegularFile(file)) return emptyList()
        return runCatching { Files.readAllLines(file, Charsets.UTF_8) }.getOrDefault(emptyList())
    }

    fun saveLog(requestId: String, lines: List<String>, partialLine: String? = null) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        val file = logFile(id)
        Files.createDirectories(file.parent)
        val text = buildString {
            append(lines.joinToString("\n"))
            if (!partialLine.isNullOrEmpty()) {
                if (isNotEmpty()) append('\n')
                append(partialLine)
            }
        }
        Files.writeString(file, text, Charsets.UTF_8)
    }

    fun clearLog(requestId: String) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        val file = runCatching { logFile(id) }.getOrNull() ?: return
        runCatching { Files.deleteIfExists(file) }
    }
}
