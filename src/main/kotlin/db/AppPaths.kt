package db

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AppPaths {

    /** 应用数据目录（库文件、SQLite 等）；与 Postman/OpenAPI 导入导出文件路径无关。 */
    fun dataDirectory(): Path {
        val os = System.getProperty("os.name").lowercase()
        val base = when {
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
        Files.createDirectories(base)
        return base
    }

    fun collectionDatabasePath(): Path = dataDirectory().resolve("collections.db")

    /** 各请求下的 response / bench 等落盘目录（非 SQLite）。 */
    fun requestArtifactsRoot(): Path = dataDirectory().resolve("request")
}
