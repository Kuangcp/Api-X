package app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import db.CollectionRepository
import kotlinx.coroutines.delay
import tree.UiCollection
import tree.requestParentLocationLabel

@Composable
fun RecentRequestSwitcherOverlay(
    requestIds: List<String>,
    highlightIndex: Int,
    tree: List<UiCollection>,
    repository: CollectionRepository,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val defaultRowHeightPx = remember(density) {
        with(density) { 40.dp.roundToPx() }.coerceAtLeast(24)
    }
    LaunchedEffect(highlightIndex, requestIds.size) {
        if (requestIds.isEmpty()) return@LaunchedEffect
        val idx = highlightIndex.coerceIn(0, requestIds.lastIndex)
        listState.ensureHighlightVisible(idx, defaultRowHeightPx)
    }

    val rows = remember(requestIds, tree, repository) {
        requestIds.map { id ->
            val r = repository.getRequest(id)
            RecentSwitcherRow(
                id = id,
                method = r?.method ?: "—",
                name = r?.name ?: "(已删除)",
                location = requestParentLocationLabel(
                    tree,
                    r?.collectionId ?: "",
                    r?.folderId,
                ),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(
                    MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = "最近请求（最多 30）· Tab 下一个 · Shift+Tab 上一个 · Esc 取消",
                style = MaterialTheme.typography.caption,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                    val selected = index == highlightIndex
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) {
                                    MaterialTheme.colors.primary.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colors.surface
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                text = row.method,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.width(44.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = row.name,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                color = MaterialTheme.colors.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (row.location.isNotEmpty()) {
                            Text(
                                text = row.location,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 66.dp, top = 1.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            val hi = highlightIndex.coerceIn(0, (requestIds.size - 1).coerceAtLeast(0))
            val label = if (requestIds.isEmpty()) {
                "无可用请求"
            } else {
                val row = rows[hi]
                "松开 Ctrl 打开：${row.method}  ${row.name}"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.body2,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 仅当高亮项未完全落在可视区内时才滚动：向下切换时尽量让列表「顶住」视口上沿，
 * 只有高亮行超出下边界才上移滚动，避免每次把选中项对齐到顶部造成抖动。
 */
private suspend fun LazyListState.ensureHighlightVisible(
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
    if (fullyVisible()) return

    val info = layoutInfo
    val vpStart = info.viewportStartOffset
    val vpEnd = info.viewportEndOffset
    val vpHeight = (vpEnd - vpStart).coerceAtLeast(1)
    val visible = info.visibleItemsInfo

    val itemInfo = visible.find { it.index == highlightIndex }
    if (itemInfo != null) {
        val top = itemInfo.offset
        val bottom = top + itemInfo.size
        when {
            top < vpStart -> scrollToItem(highlightIndex, scrollOffset = 0)
            bottom > vpEnd -> scrollToItem(
                highlightIndex,
                scrollOffset = (vpHeight - itemInfo.size).coerceAtLeast(0),
            )
        }
        return
    }

    if (visible.isEmpty()) {
        scrollToItem(highlightIndex)
        return
    }
    val firstIdx = visible.first().index
    val lastIdx = visible.last().index
    when {
        highlightIndex < firstIdx -> scrollToItem(highlightIndex, scrollOffset = 0)
        highlightIndex > lastIdx -> {
            val refH = visible.last().size.takeIf { it > 0 } ?: defaultItemHeightPx
            scrollToItem(
                highlightIndex,
                scrollOffset = (vpHeight - refH).coerceAtLeast(0),
            )
            delay(16)
            val info2 = layoutInfo
            val item2 = info2.visibleItemsInfo.find { it.index == highlightIndex }
            if (item2 != null) {
                val vpE = info2.viewportEndOffset
                val vpS = info2.viewportStartOffset
                val vpH = (vpE - vpS).coerceAtLeast(1)
                val bottom = item2.offset + item2.size
                if (bottom > vpE) {
                    scrollToItem(
                        highlightIndex,
                        scrollOffset = (vpH - item2.size).coerceAtLeast(0),
                    )
                } else if (item2.offset < vpS) {
                    scrollToItem(highlightIndex, scrollOffset = 0)
                }
            }
        }
    }
}

private data class RecentSwitcherRow(
    val id: String,
    val method: String,
    val name: String,
    val location: String,
)
