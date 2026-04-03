package app

import db.AppPaths
import java.nio.file.Files
import java.util.Properties

/** 侧栏树中 Collection / Folder 的展开状态，存于应用数据目录。 */
object TreeExpandPrefs {

    private const val KEY_COLLECTIONS = "expandedCollections"
    private const val KEY_FOLDERS = "expandedFolders"

    data class Loaded(
        val collectionIds: Set<String>,
        val folderIds: Set<String>,
        /** 为 true 表示曾从配置文件读取过（含空集合），用于与「从未保存过」区分。 */
        val fromSavedFile: Boolean,
    )

    private fun path() = AppPaths.dataDirectory().resolve("tree-expand.properties")

    private fun parseIdList(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',').mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
    }

    fun load(): Loaded {
        val file = path()
        if (!Files.isRegularFile(file)) {
            return Loaded(collectionIds = emptySet(), folderIds = emptySet(), fromSavedFile = false)
        }
        return runCatching {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            Loaded(
                collectionIds = parseIdList(props.getProperty(KEY_COLLECTIONS)),
                folderIds = parseIdList(props.getProperty(KEY_FOLDERS)),
                fromSavedFile = true,
            )
        }.getOrElse {
            Loaded(collectionIds = emptySet(), folderIds = emptySet(), fromSavedFile = false)
        }
    }

    fun save(collectionIds: Set<String>, folderIds: Set<String>) {
        runCatching {
            val props = Properties()
            props.setProperty(KEY_COLLECTIONS, collectionIds.sorted().joinToString(","))
            props.setProperty(KEY_FOLDERS, folderIds.sorted().joinToString(","))
            Files.newOutputStream(path()).use { out ->
                props.store(out, "api-x tree expand state")
            }
        }
    }
}
