package db

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.stream.Collectors
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

@Serializable
data class CachedHttpResponse(
    val savedAtEpochMs: Long,
    val statusCodeText: String,
    val responseTimeText: String,
    val responseSizeText: String,
    val responseBodyLines: List<String>,
    val responseHeaderLines: List<String>,
    val isSseResponse: Boolean,
    val rightTabIndex: Int,
)

object RequestResponseStore {

    private const val MAX_FILES_PER_REQUEST = 10

    private fun requestDir(requestId: String): Path =
        AppPaths.requestArtifactsRoot().resolve(sanitizeRequestId(requestId))

    private fun responseDir(requestId: String): Path = requestDir(requestId).resolve("response")

    private fun benchDir(requestId: String): Path = requestDir(requestId).resolve("bench")

    private fun sanitizeRequestId(id: String): String {
        val t = id.trim()
        require(t.isNotEmpty() && !t.contains("..") && t.none { it == '/' || it == '\\' }) {
            "非法 request id"
        }
        return t
    }

    private fun listResponseJsonFiles(dir: Path): List<Path> {
        if (dir.notExists() || !dir.isDirectory()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { p ->
                Files.isRegularFile(p) && p.fileName.toString().endsWith(".json", ignoreCase = true)
            }.collect(Collectors.toList())
        }
    }

    fun ensureLayout(requestId: String) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        Files.createDirectories(responseDir(id))
        Files.createDirectories(benchDir(id))
    }

    fun loadLatest(requestId: String): CachedHttpResponse? {
        val id = requestId.trim()
        if (id.isEmpty()) return null
        val dir = responseDir(id)
        val files = listResponseJsonFiles(dir)
        if (files.isEmpty()) return null
        val latest = files.maxWithOrNull(Comparator.comparingLong { p ->
            p.fileName.toString().removeSuffix(".json").removeSuffix(".JSON").toLongOrNull() ?: 0L
        }) ?: return null
        return runCatching {
            val text = Files.readString(latest, Charsets.UTF_8)
            HarLogCodec.parseToCachedResponse(text)
        }.getOrNull()
    }

    fun save(requestId: String, snapshot: HarSnapshot) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        ensureLayout(id)
        val dir = responseDir(id)
        val file = dir.resolve("${snapshot.savedAtEpochMs}.json")
        val text = HarLogCodec.toJsonString(snapshot)
        Files.writeString(file, text, Charsets.UTF_8)
        pruneOld(dir)
    }

    private fun pruneOld(dir: Path) {
        val files = listResponseJsonFiles(dir).sortedWith(
            Comparator.comparingLong { p: Path ->
                p.fileName.toString().removeSuffix(".json").removeSuffix(".JSON").toLongOrNull() ?: 0L
            }.reversed()
        )
        if (files.size <= MAX_FILES_PER_REQUEST) return
        files.drop(MAX_FILES_PER_REQUEST).forEach { p ->
            runCatching { Files.deleteIfExists(p) }
        }
    }

    fun deleteRequestArtifacts(requestId: String) {
        val root = runCatching { requestDir(requestId.trim()) }.getOrNull() ?: return
        if (root.notExists()) return
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { p ->
                runCatching { Files.deleteIfExists(p) }
            }
        }
    }
}
