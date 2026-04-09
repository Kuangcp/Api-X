package app

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.delay

/**
 * 将高亮项滚入可视区：一次 [scrollToItem]（Compose Desktop 当前无独立的 [animateScrollToItem] 符号时与多段 scroll 相比更稳）。
 * [defaultItemHeightPx] 保留与调用方 API 一致。
 */
suspend fun LazyListState.ensureHighlightVisible(
    highlightIndex: Int,
    @Suppress("UNUSED_PARAMETER") defaultItemHeightPx: Int,
) {
    if (layoutInfo.totalItemsCount == 0) return
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
    if (fullyVisible()) return

    val idx = highlightIndex.coerceIn(0, layoutInfo.totalItemsCount - 1)
    scrollToItem(idx)
}
