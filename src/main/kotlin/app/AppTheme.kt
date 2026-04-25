package app

import androidx.compose.ui.graphics.Color
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors

private val lightThemeDefaultBackground = hexToColor("#EBECF0")

internal fun appMaterialColors(isDark: Boolean, backgroundHex: String) =
    parseHexColorOrNull(backgroundHex).let { customBg ->
        when {
            isDark -> {
                val base = apiXDarkColors()
                if (customBg != null) base.copy(background = customBg) else base
            }
            else -> {
                val bg = when {
                    customBg == null -> lightThemeDefaultBackground
                    customBg.isVisuallyDarkBackground() -> lightThemeDefaultBackground
                    else -> customBg
                }
                lightColors(background = bg)
            }
        }
    }

internal fun apiXDarkColors() = darkColors(
    primary = Color(0xFF90CAF9),
    primaryVariant = Color(0xFF42A5F5),
    secondary = Color(0xFF90CAF9),
    background = Color(0xFF292B2E),
    surface = Color(0xFF32353B),
    error = Color(0xFFCF6679),
    onPrimary = Color(0xFF0D47A1),
    onSecondary = Color(0xFF0D47A1),
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black
)

internal fun hexToColor(hex: String): Color {
    val value = hex.removePrefix("#")
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        else -> throw IllegalArgumentException("颜色格式错误: $hex，需为 #RRGGBB 或 #AARRGGBB")
    }

    val alpha = argb.substring(0, 2).toInt(16)
    val red = argb.substring(2, 4).toInt(16)
    val green = argb.substring(4, 6).toInt(16)
    val blue = argb.substring(6, 8).toInt(16)
    return Color(red, green, blue, alpha)
}

private fun hexToColorCode(hex: String): ULong {
    val value = hex.removePrefix("#")
    val argb = when (value.length) {
        6 -> "FF$value"
        8 -> value
        else -> throw IllegalArgumentException("颜色格式错误: $hex，需为 #RRGGBB 或 #AARRGGBB")
    }
    return ("0x$argb").removePrefix("0x").toULong(16)
}