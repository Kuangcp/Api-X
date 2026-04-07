package app

import db.AppPaths
import java.nio.file.Files
import java.util.Properties

/**
 * 记录请求在编辑器中最近一次打开/查看时间，用于 Ctrl+Tab 最近列表（最多保留 30 条）。
 */
object RecentRequestUsageStore {

    private const val MAX_ENTRIES = 30

    private fun path() = AppPaths.dataDirectory().resolve("recent-request-usage.properties")

    fun touch(requestId: String) {
        if (requestId.isBlank()) return
        val now = System.currentTimeMillis()
        val entries = loadEntries().toMutableMap()
        entries[requestId] = now
        trimToMax(entries)
        save(entries)
    }

    fun remove(requestId: String) {
        val entries = loadEntries().toMutableMap()
        if (entries.remove(requestId) != null) save(entries)
    }

    /** 按最近查看时间倒序；仅包含 [valid] 为 true 的 id，最多 [MAX_ENTRIES] 条。 */
    fun orderedIdsNewestFirst(valid: (String) -> Boolean): List<String> {
        return loadEntries()
            .filter { valid(it.key) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key },
            )
            .map { it.key }
            .take(MAX_ENTRIES)
    }

    private fun trimToMax(entries: MutableMap<String, Long>) {
        while (entries.size > MAX_ENTRIES) {
            val drop = entries.minByOrNull { it.value }?.key ?: return
            entries.remove(drop)
        }
    }

    private fun loadEntries(): Map<String, Long> {
        val file = path()
        if (!Files.isRegularFile(file)) return emptyMap()
        return runCatching {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            props.stringPropertyNames().mapNotNull { name ->
                val ms = props.getProperty(name)?.toLongOrNull() ?: return@mapNotNull null
                name to ms
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    private fun save(entries: Map<String, Long>) {
        runCatching {
            val dir = path().parent
            if (dir != null) Files.createDirectories(dir)
            val props = Properties()
            for ((id, ms) in entries) {
                props.setProperty(id, ms.toString())
            }
            Files.newOutputStream(path()).use { out ->
                props.store(out, "api-x recent request usage")
            }
        }
    }
}
