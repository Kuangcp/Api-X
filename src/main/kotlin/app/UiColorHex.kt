package app

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** 解析界面背景 HEX，支持 `#RGB`、`#RRGGBB`、`#AARRGGBB`；无效返回 null。 */
fun parseHexColorOrNull(input: String): Color? {
    val value = input.trim().removePrefix("#").trim()
    if (value.isEmpty()) return null
    val argb = when (value.length) {
        6 -> {
            if (!value.all { it.digitToIntOrNull(16) != null }) return null
            "FF$value"
        }
        8 -> {
            if (!value.all { it.digitToIntOrNull(16) != null }) return null
            value
        }
        3 -> {
            if (!value.all { it.digitToIntOrNull(16) != null }) return null
            val expanded = value.map { "$it$it" }.joinToString("")
            "FF$expanded"
        }
        else -> return null
    }
    return runCatching {
        val alpha = argb.substring(0, 2).toInt(16)
        val red = argb.substring(2, 4).toInt(16)
        val green = argb.substring(4, 6).toInt(16)
        val blue = argb.substring(6, 8).toInt(16)
        Color(red, green, blue, alpha)
    }.getOrNull()
}

/**
 * 判断背景是否偏深。浅色 Material 主题的 onBackground 为深色字；
 * 若仍套用深色自定义背景会导致对比度崩溃。
 */
internal fun Color.isVisuallyDarkBackground(): Boolean {
    fun linearize(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return luminance < 0.45f
}

/** 非空时必须可解析；空串表示使用主题默认背景。 */
fun validateBackgroundHexField(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return if (parseHexColorOrNull(t) != null) {
        null
    } else {
        "背景色格式无效，请使用 #RRGGBB 或 #RGB（如 #282923）"
    }
}
