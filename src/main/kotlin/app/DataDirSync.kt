package app

import db.AppPaths
import db.CollectionRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import tree.parsePostmanCollectionJsonToPortable
import tree.portableCollectionToPostmanV21Json

private val envJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

data class DataPushResult(
    val collectionFilesWritten: Int,
    val envWritten: Boolean,
    val error: String? = null,
)

data class DataPullResult(
    val merged: Int,
    val created: Int,
    val envMerged: Boolean,
    val fileErrors: List<String> = emptyList(),
    val error: String? = null,
)

object DataDirSync {

    /**
     * 将当前库中各集合导出为 `data/collection/{id}.json`（Postman v2.1），
     * 环境写入 `data/env/environments.json`（主存文件或当前 [EnvironmentStore] 快照）。
     */
    fun pushToDataDir(repository: CollectionRepository): DataPushResult {
        return try {
            val collDir = AppPaths.gitDataCollectionDir()
            val envFile = AppPaths.gitDataEnvironmentsFile()
            var n = 0
            for (id in repository.listCollectionIds()) {
                val p = repository.exportPortableCollection(id) ?: continue
                val json = portableCollectionToPostmanV21Json(p)
                val file = collDir.resolve("$id.json")
                Files.writeString(file, json, StandardCharsets.UTF_8)
                n++
            }
            val mainEnv = AppPaths.environmentsJsonPath()
            var envOk = false
            if (Files.isRegularFile(mainEnv)) {
                Files.createDirectories(envFile.parent)
                Files.copy(mainEnv, envFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                envOk = true
            } else {
                if (envFile.parent != null) Files.createDirectories(envFile.parent)
                EnvironmentStore.writeSnapshotToPath(envFile)
                envOk = true
            }
            DataPushResult(n, envOk, null)
        } catch (e: Exception) {
            DataPushResult(0, false, e.message ?: e.toString())
        }
    }

    /**
     * 从 [data] 下加载：集合仅新增与按 id 更新，不删库内已有行；环境为按 id 合并、不删。
     * @param data 通常为 [AppPaths.gitDataRoot]。
     */
    fun pullFromDataDir(repository: CollectionRepository, data: Path = AppPaths.gitDataRoot()): DataPullResult {
        return try {
            var merged = 0
            var created = 0
            val fileErr = mutableListOf<String>()
            val collDir = data.resolve("collection")
            if (Files.isDirectory(collDir)) {
                Files.list(collDir).use { stream ->
                    stream.filter { f ->
                        val name = f.fileName.toString()
                        name.endsWith(".json", ignoreCase = true) && Files.isRegularFile(f)
                    }.forEach { path ->
                        runCatching {
                            val text = Files.readString(path, StandardCharsets.UTF_8)
                            val portable = parsePostmanCollectionJsonToPortable(text)
                            val idFromName = path.fileName.toString().removeSuffix(".json")
                            if (idFromName.isBlank()) {
                                return@forEach
                            }
                            if (repository.collectionExists(idFromName)) {
                                repository.mergePortableIntoCollection(idFromName, portable)
                                merged++
                            } else {
                                repository.importAsNewCollectionWithFixedId(idFromName, portable)
                                created++
                            }
                        }.onFailure { e ->
                            fileErr += "${path.fileName}: ${e.message ?: e::class.java.simpleName}"
                        }
                    }
                }
            }
            val envInData = data.resolve("env").resolve("environments.json")
            var envMerged = false
            if (Files.isRegularFile(envInData)) {
                val fromFile: EnvironmentsState = runCatching {
                    envJson.decodeFromString<EnvironmentsState>(
                        Files.readString(envInData, StandardCharsets.UTF_8),
                    )
                }.getOrElse { EnvironmentsState() }
                val mergedState = mergeEnvironmentsStateNoDelete(EnvironmentStore.snapshot(), fromFile)
                EnvironmentStore.replace(mergedState)
                envMerged = true
            }
            DataPullResult(merged, created, envMerged, fileErr, null)
        } catch (e: Exception) {
            DataPullResult(0, 0, false, emptyList(), e.message ?: e.toString())
        }
    }
}
