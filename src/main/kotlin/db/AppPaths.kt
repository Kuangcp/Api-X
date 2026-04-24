package db

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

object AppPaths {

    private const val KEY_DEBUG_HOME = "debugHome"

    /**
     * 不经过 [debugHome] 重定向的平台默认目录（XDG / APPDATA 等）。
     * 仅本对象内用于读取「主目录」下的 `app-settings.properties` 以解析 [KEY_DEBUG_HOME]。
     */
    private fun platformDefaultDataRoot(): Path {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) Paths.get(appData, "api-x")
                else Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "api-x")
            }
            os.contains("mac") -> Paths.get(
                System.getProperty("user.home"),
                "Library",
                "Application Support",
                "api-x"
            )
            else -> {
                val xdg = System.getenv("XDG_DATA_HOME")
                if (!xdg.isNullOrBlank()) Paths.get(xdg, "api-x")
                else Paths.get(System.getProperty("user.home"), ".local", "share", "api-x")
            }
        }
    }

    /**
     * 从**主目录**（[platformDefaultDataRoot]）下的 [appSettingsBootstrapFile] 读取
     * `debugHome=/path/to/dir`：若已配置且非空，则**所有**数据（库、环境、data 同步目录等）使用该路径作为根，便于在空目录调试而不影响正式数据。
     * 重定向只认主目录里这一份文件，避免与 [dataDirectory] 自身形成循环。
     */
    private fun dataRootFromDebugHomeIfConfigured(): Path? {
        val f = appSettingsBootstrapFile
        if (!Files.isRegularFile(f)) return null
        return runCatching {
            val props = Properties()
            Files.newInputStream(f).use { props.load(it) }
            val raw = props.getProperty(KEY_DEBUG_HOME)?.trim().orEmpty()
            if (raw.isEmpty()) return@runCatching null
            val p = Paths.get(raw).toAbsolutePath().normalize()
            if (!Files.isDirectory(p)) {
                Files.createDirectories(p)
            }
            p
        }.getOrNull()
    }

    /** 主数据目录中仅用于解析 `debugHome` 的 `app-settings.properties` 路径（不经重定向）。 */
    private val appSettingsBootstrapFile: Path
        get() = platformDefaultDataRoot().resolve("app-settings.properties")

    @Volatile
    private var cachedDataDirectory: Path? = null

    private val dataDirectoryLock = Any()

    /**
     * 应用数据目录（库文件、SQLite 等）；与 Postman/OpenAPI 导入导出文件路径无关。
     *
     * 若在主目录的 `app-settings.properties` 中设置 `debugHome=绝对路径`（或用户目录下的路径），
     * 则整个应用的数据根目录切换至该路径；未设置时仍为平台默认目录。首次解析后结果会缓存到进程内。
     */
    fun dataDirectory(): Path {
        cachedDataDirectory?.let { return it }
        return synchronized(dataDirectoryLock) {
            cachedDataDirectory?.let { return it }
            val def = platformDefaultDataRoot()
            val chosen = dataRootFromDebugHomeIfConfigured() ?: def
            Files.createDirectories(chosen)
            cachedDataDirectory = chosen
            chosen
        }
    }

    fun collectionDatabasePath(): Path = dataDirectory().resolve("collections.db")

    /**
     * 可纳入 Git 的数据根目录：`<appData>/data/`，下含 `collection/`、`env/`。
     * 与 [dataDirectory]（库、SQLite 根）是父子关系，勿混淆。
     */
    fun gitDataRoot(): Path {
        val p = dataDirectory().resolve("data")
        Files.createDirectories(p)
        return p
    }

    fun gitDataCollectionDir(): Path {
        val p = gitDataRoot().resolve("collection")
        Files.createDirectories(p)
        return p
    }

    fun gitDataEnvDir(): Path {
        val p = gitDataRoot().resolve("env")
        Files.createDirectories(p)
        return p
    }

    /** 同步目录中的环境文件（与主存 [environmentsJsonPath] 成对，供 Push/Pull 使用）。 */
    fun gitDataEnvironmentsFile(): Path = gitDataEnvDir().resolve("environments.json")

    /** Postman 风格环境变量（`{{name}}`），JSON 持久化。 */
    fun environmentsJsonPath(): Path = dataDirectory().resolve("environments.json")

    /** 各请求下的 response / bench 等落盘目录（非 SQLite）。 */
    fun requestArtifactsRoot(): Path = dataDirectory().resolve("request")
}
