package http

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val REF_SP = 13f

/** Request / Response 主编辑区字号基准（与原先约 12–13sp 对齐）。 */
data class ExchangeFontMetrics(
    val body: TextUnit,
    val tab: TextUnit,
    val compact: TextUnit,
    val tiny: TextUnit,
    val urlBarHeight: Dp,
    val editorTabStripHeight: Dp,
)

fun exchangeFontMetrics(fontSizeSp: Float): ExchangeFontMetrics {
    val b = fontSizeSp.coerceIn(9f, 28f)
    val r = b / REF_SP
    return ExchangeFontMetrics(
        body = (13f * r).sp,
        tab = (12f * r).sp,
        compact = (11f * r).sp,
        tiny = (10f * r).sp,
        urlBarHeight = (39f * r.coerceAtMost(1.5f)).dp,
        editorTabStripHeight = (24f * r.coerceAtMost(1.35f)).dp,
    )
}
