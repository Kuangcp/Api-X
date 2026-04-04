package app

import androidx.compose.material.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
fun fontFamilyFromSettings(name: String): FontFamily {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return FontFamily.Default
    return runCatching { FontFamily(SystemFont(trimmed)) }.getOrElse { FontFamily.Default }
}

/** 以 Material 默认排版为基准，整体缩放正文字号并统一字体族。 */
@OptIn(ExperimentalTextApi::class)
fun typographyFromSettings(settings: AppSettings): Typography {
    val base = Typography()
    val fontFamily = fontFamilyFromSettings(settings.fontFamilyName)
    val scale = settings.fontSizeSp / 14f
    fun TextStyle.scaled() = copy(
        fontFamily = fontFamily,
        fontSize = (fontSize.value * scale).sp,
    )
    return Typography(
        h1 = base.h1.scaled(),
        h2 = base.h2.scaled(),
        h3 = base.h3.scaled(),
        h4 = base.h4.scaled(),
        h5 = base.h5.scaled(),
        h6 = base.h6.scaled(),
        subtitle1 = base.subtitle1.scaled(),
        subtitle2 = base.subtitle2.scaled(),
        body1 = base.body1.scaled(),
        body2 = base.body2.scaled(),
        button = base.button.scaled(),
        caption = base.caption.scaled(),
        overline = base.overline.scaled(),
    )
}
