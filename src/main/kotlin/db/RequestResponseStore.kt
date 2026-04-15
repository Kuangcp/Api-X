package db

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Comparator
import java.util.stream.Collectors
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

private val historyTimeFormatter: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
}

private val historyDateTimeFormatter: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
}

fun formatHistoryTime(epochMs: Long, todayMs: Long): String {
    val instant = Instant.ofEpochMilli(epochMs)
    val today = Instant.ofEpochMilli(todayMs)
    val todayStart = today.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
    return if (epochMs >= todayStart.toEpochMilli()) {
        historyTimeFormatter.format(instant)
    } else {
        historyDateTimeFormatter.format(instant)
    }
}

data class HistoryEntry(
    val epochMs: Long,
    val displayTime: String,
)

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
    /** 最近一次交换中实际发出的请求（纯文本），从 HAR 的 request 段还原。 */
    val requestPlainText: String = "",
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

    private fun responseLogStemEpochMs(fileName: String): Long {
        val n = fileName.lowercase()
        val stem = when {
            n.endsWith(".har") -> fileName.dropLast(4)
            n.endsWith(".json") -> fileName.dropLast(5)
            else -> return 0L
        }
        return stem.toLongOrNull() ?: 0L
    }

    /** 当前使用 `.har`；仍扫描 `.json` 以便读取升级前的旧文件。 */
    private fun listResponseLogFiles(dir: Path): List<Path> {
        if (dir.notExists() || !dir.isDirectory()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { p ->
                val name = p.fileName.toString()
                Files.isRegularFile(p) && (
                    name.endsWith(".har", ignoreCase = true) ||
                        name.endsWith(".json", ignoreCase = true)
                    )
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
        val files = listResponseLogFiles(dir)
        if (files.isEmpty()) return null
        val latest = files.maxWithOrNull(Comparator.comparingLong { p ->
            responseLogStemEpochMs(p.fileName.toString())
        }) ?: return null
        return runCatching {
            val text = Files.readString(latest, Charsets.UTF_8)
            HarLogCodec.parseToCachedResponse(text)
        }.getOrNull()
    }

    fun listHistory(requestId: String): List<HistoryEntry> {
        val id = requestId.trim()
        if (id.isEmpty()) return emptyList()
        val dir = responseDir(id)
        val files = listResponseLogFiles(dir)
        if (files.isEmpty()) return emptyList()
        val todayMs = System.currentTimeMillis()
        return files.mapNotNull { path ->
            val epochMs = responseLogStemEpochMs(path.fileName.toString())
            if (epochMs > 0) {
                HistoryEntry(epochMs, formatHistoryTime(epochMs, todayMs))
            } else null
        }.sortedByDescending { it.epochMs }.take(10)
    }

    fun loadByTimestamp(requestId: String, epochMs: Long): CachedHttpResponse? {
        val id = requestId.trim()
        if (id.isEmpty()) return null
        val dir = responseDir(id)
        val fileName = "$epochMs.har"
        val jsonFileName = "$epochMs.json"
        val file = dir.resolve(fileName)
        val jsonFile = dir.resolve(jsonFileName)
        val targetFile = if (Files.exists(file)) file else if (Files.exists(jsonFile)) jsonFile else null
        return targetFile?.let { f ->
            runCatching {
                val text = Files.readString(f, Charsets.UTF_8)
                HarLogCodec.parseToCachedResponse(text)
            }.getOrNull()
        }
    }

    fun save(requestId: String, snapshot: HarSnapshot) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        ensureLayout(id)
        val dir = responseDir(id)
        val file = dir.resolve("${snapshot.savedAtEpochMs}.har")
        val text = HarLogCodec.toJsonString(snapshot)
        Files.writeString(file, text, Charsets.UTF_8)
        pruneOld(dir)
    }

    private fun pruneOld(dir: Path) {
        val files = listResponseLogFiles(dir).sortedWith(
            Comparator.comparingLong { p: Path ->
                responseLogStemEpochMs(p.fileName.toString())
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

    /** 删除该请求下已落盘的响应 HAR/JSON 与 bench 目录内全部内容（含子目录）。 */
    fun clearResponseAndBenchLogs(requestId: String) {
        val id = requestId.trim()
        if (id.isEmpty()) return
        fun wipeSubtree(root: Path) {
            if (root.notExists()) return
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { p ->
                    runCatching { Files.deleteIfExists(p) }
                }
            }
        }
        wipeSubtree(responseDir(id))
        wipeSubtree(benchDir(id))
    }
}
