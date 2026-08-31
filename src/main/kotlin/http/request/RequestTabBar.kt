package http.request

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tree.UiCollection
import tree.UiFolder

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RequestTabBar(
    openTabIds: List<String>,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    tree: List<UiCollection>,
    modifier: Modifier = Modifier,
    multiLine: Boolean = false,
    onCloseAll: () -> Unit = {},
    onCloseOthers: (String) -> Unit = {},
) {
    val requestInfoById = remember(tree) {
        buildMap<String, Pair<String, String>> {
            for (col in tree) {
                for (req in col.rootRequests) put(req.id, req.method to req.name)
                fun walk(folder: UiFolder) {
                    for (req in folder.requests) put(req.id, req.method to req.name)
                    for (child in folder.children) walk(child)
                }
                for (folder in col.folders) walk(folder)
            }
        }
    }

    if (multiLine) {
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.background),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            for (tabId in openTabIds) {
                val info = requestInfoById[tabId]
                RequestTab(
                    tabId = tabId,
                    name = info?.second?.ifBlank { "(未命名)" } ?: "(未知)",
                    method = info?.first?.uppercase() ?: "GET",
                    isActive = tabId == activeTabId,
                    onSelected = onTabSelected,
                    onClosed = onTabClosed,
                    onCloseAll = onCloseAll,
                    onCloseOthers = onCloseOthers,
                )
            }
        }
        return
    }

    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colors.background)
            .horizontalScroll(scrollState)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: continue
                        val delta = change.scrollDelta
                        val total = delta.y + delta.x
                        if (total != 0f) {
                            scrollState.dispatchRawDelta(total * 60f)
                            change.consume()
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tabId in openTabIds) {
            val info = requestInfoById[tabId]
            RequestTab(
                tabId = tabId,
                name = info?.second?.ifBlank { "(未命名)" } ?: "(未知)",
                method = info?.first?.uppercase() ?: "GET",
                isActive = tabId == activeTabId,
                onSelected = onTabSelected,
                onClosed = onTabClosed,
                onCloseAll = onCloseAll,
                onCloseOthers = onCloseOthers,
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RequestTab(
    tabId: String,
    name: String,
    method: String,
    isActive: Boolean,
    onSelected: (String) -> Unit,
    onClosed: (String) -> Unit,
    onCloseAll: () -> Unit,
    onCloseOthers: (String) -> Unit,
) {
    val methodColor = when (method) {
        "GET" -> Color(0xFF4CAF50)
        "POST" -> MaterialTheme.colors.primary
        else -> Color(0xFFE65100)
    }
    val bgColor = if (isActive) {
        MaterialTheme.colors.surface
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.04f)
    }
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("关闭其他") { onCloseOthers(tabId) },
                ContextMenuItem("关闭全部") { onCloseAll() },
            )
        }
    ) {
        Row(
            modifier = Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp, 4.dp, 0.dp, 0.dp))
                .background(bgColor)
                .clickable { onSelected(tabId) }
                .padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Text(
            method,
            style = MaterialTheme.typography.body2,
            color = methodColor,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(
                alpha = if (isActive) 1f else ContentAlpha.medium,
            ),
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable { onClosed(tabId) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colors.onSurface.copy(
                    alpha = ContentAlpha.medium,
                ),
            )
        }
        }
    }
}
