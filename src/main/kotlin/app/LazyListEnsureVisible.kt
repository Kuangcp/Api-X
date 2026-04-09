package app

import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * 仅当高亮项未完全落在可视区内时才滚动。
 * - 有溢出时，每次只滚动 **min(溢出像素, 当前行高)**，避免一次对齐整屏。
 * - 高亮尚在视窗外时，每次只滚 **一行高度**（[defaultItemHeightPx]），与 Ctrl+Tab 列表一致。
 */
suspend fun LazyListState.ensureHighlightVisible(
    highlightIndex: Int,
    defaultItemHeightPx: Int,
) {
    if (layoutInfo.totalItemsCount == 0) return
    delay(16)
    var wait = 0
    while (layoutInfo.visibleItemsInfo.isEmpty() && wait < 8) {
        delay(16)
        wait++
    }

    fun fullyVisible(): Boolean {
        val info = layoutInfo
        val vpStart = info.viewportStartOffset
        val vpEnd = info.viewportEndOffset
        val item = info.visibleItemsInfo.find { it.index == highlightIndex } ?: return false
        val top = item.offset
        val bottom = top + item.size
        return top >= vpStart && bottom <= vpEnd
    }

    val stepPx = defaultItemHeightPx.coerceAtLeast(1).toFloat()
    var guard = 0
    while (guard++ < 64) {
        if (fullyVisible()) return

        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            scrollToItem(highlightIndex.coerceIn(0, layoutInfo.totalItemsCount - 1))
            delay(16)
            continue
        }

        val itemInfo = visible.find { it.index == highlightIndex }
        if (itemInfo != null) {
            val vpStart = layoutInfo.viewportStartOffset
            val vpEnd = layoutInfo.viewportEndOffset
            val top = itemInfo.offset
            val bottom = top + itemInfo.size
            val rowH = itemInfo.size.coerceAtLeast(1)
            when {
                top < vpStart -> {
                    val overflow = (vpStart - top).coerceAtLeast(1)
                    val step = min(overflow, rowH)
                    scrollByPixels(-step.toFloat())
                }
                bottom > vpEnd -> {
                    val overflow = (bottom - vpEnd).coerceAtLeast(1)
                    val step = min(overflow, rowH)
                    scrollByPixels(step.toFloat())
                }
                else -> return
            }
            delay(16)
            continue
        }

        val firstIdx = visible.first().index
        val lastIdx = visible.last().index
        when {
            highlightIndex < firstIdx -> scrollByPixels(-stepPx)
            highlightIndex > lastIdx -> scrollByPixels(stepPx)
            else -> return
        }
        delay(16)
    }
}

/** 在 [LazyListState.scroll] 会话内按像素平移，用于小步长滚动。 */
private suspend fun LazyListState.scrollByPixels(deltaPx: Float) {
    if (deltaPx == 0f) return
    scroll {
        scrollBy(deltaPx)
    }
}
