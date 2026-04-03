package app

import db.AppPaths
import java.nio.file.Files
import java.util.Properties

/** 主窗口几何信息：存为 dp 浮点，与 [androidx.compose.ui.window.WindowState] 一致。 */
object WindowPrefs {

    private const val KEY_X = "xDp"
    private const val KEY_Y = "yDp"
    private const val KEY_W = "widthDp"
    private const val KEY_H = "heightDp"

    private const val DEFAULT_W = 1200f
    private const val DEFAULT_H = 800f
    private const val MIN_W = 400f
    private const val MIN_H = 300f
    private const val MAX_W = 10000f
    private const val MAX_H = 10000f

    private fun path() = AppPaths.dataDirectory().resolve("window.properties")

    data class Geometry(
        val xDp: Float?,
        val yDp: Float?,
        val widthDp: Float,
        val heightDp: Float,
    )

    fun load(): Geometry {
        val file = path()
        if (!Files.isRegularFile(file)) {
            return Geometry(xDp = null, yDp = null, widthDp = DEFAULT_W, heightDp = DEFAULT_H)
        }
        return runCatching {
            val props = Properties()
            Files.newInputStream(file).use { props.load(it) }
            fun num(key: String) = props.getProperty(key)?.toFloatOrNull()
            Geometry(
                xDp = num(KEY_X),
                yDp = num(KEY_Y),
                widthDp = num(KEY_W)?.coerceIn(MIN_W, MAX_W) ?: DEFAULT_W,
                heightDp = num(KEY_H)?.coerceIn(MIN_H, MAX_H) ?: DEFAULT_H,
            )
        }.getOrElse {
            Geometry(xDp = null, yDp = null, widthDp = DEFAULT_W, heightDp = DEFAULT_H)
        }
    }

    fun save(xDp: Float, yDp: Float, widthDp: Float, heightDp: Float) {
        runCatching {
            val props = Properties()
            props.setProperty(KEY_X, xDp.toString())
            props.setProperty(KEY_Y, yDp.toString())
            props.setProperty(KEY_W, widthDp.coerceIn(MIN_W, MAX_W).toString())
            props.setProperty(KEY_H, heightDp.coerceIn(MIN_H, MAX_H).toString())
            Files.newOutputStream(path()).use { out ->
                props.store(out, "api-x main window geometry (dp)")
            }
        }
    }
}
