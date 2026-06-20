package db

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.notExists

object McpDraftStore {
    private fun requestDir(requestId: String): Path =
        AppPaths.requestArtifactsRoot().resolve(sanitizeRequestId(requestId))

    private fun draftsDir(requestId: String): Path =
        requestDir(requestId).resolve("mcp").resolve("drafts")

    private fun sanitizeRequestId(id: String): String {
        val t = id.trim()
        require(t.isNotEmpty() && !t.contains("..") && t.none { it == '/' || it == '\\' }) {
            "Illegal request id"
        }
        return t
    }

    private fun draftFile(requestId: String, method: String, identifier: String): Path {
        val rawKey = "${method.trim()}|${identifier.trim()}"
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rawKey.toByteArray(Charsets.UTF_8))
        return draftsDir(requestId).resolve("$encoded.json")
    }

    fun loadDraft(requestId: String, method: String, identifier: String): String? {
        val id = requestId.trim()
        if (id.isEmpty()) return null
        val file = runCatching { draftFile(id, method, identifier) }.getOrNull() ?: return null
        if (file.notExists() || !Files.isRegularFile(file)) return null
        return runCatching { Files.readString(file, Charsets.UTF_8) }.getOrNull()
    }

    fun saveDraft(requestId: String, method: String, identifier: String, bodyText: String) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        val file = draftFile(id, method, identifier)
        Files.createDirectories(file.parent)
        Files.writeString(file, bodyText, Charsets.UTF_8)
    }
}
