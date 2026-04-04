package app

import androidx.compose.ui.graphics.Color

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
