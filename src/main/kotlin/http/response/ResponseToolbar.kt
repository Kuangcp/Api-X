package http.response

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ui.CustomIcons
import db.HistoryEntry
import http.ExchangeFontMetrics
import http.HttpExchangeErrorStatusMark

@Composable
internal fun ResponseToolbar(
    exchangeMetrics: ExchangeFontMetrics,
    statusCodeText: String,
    responseTimeText: String,
    responseSizeText: String,
    responseSseEventCount: String,
    responseSseTtftText: String = "",
    responseSseTpotText: String = "",
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    copyResponseBodyEnabled: Boolean,
    onCopyResponseBody: () -> Unit,
    clearResponseLogsEnabled: Boolean,
    onClearResponseLogs: () -> Unit,
    showMcpCatalogRefresh: Boolean,
    mcpCatalogRefreshEnabled: Boolean,
    onRefreshMcpCatalog: () -> Unit,
    showMcpConnectionControls: Boolean,
    isMcpConnected: Boolean,
    mcpConnectEnabled: Boolean,
    mcpReconnectEnabled: Boolean,
    mcpDisconnectEnabled: Boolean,
    onMcpConnect: () -> Unit,
    onMcpReconnect: () -> Unit,
    onMcpDisconnect: () -> Unit,
    historyEntries: List<HistoryEntry>,
    selectedHistoryEpochMs: Long?,
    onHistorySelected: (Long?) -> Unit,
) {
    val darkTheme = !MaterialTheme.colors.isLight
    val metaColor = MaterialTheme.colors.onSurface
    val tab = exchangeMetrics.tab

    Row(
        modifier = Modifier.fillMaxWidth().height(exchangeMetrics.urlBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusCodeText,
                fontSize = tab,
                color = when {
                    statusCodeText == HttpExchangeErrorStatusMark ->
                        if (darkTheme) Color(0xFFFF5252) else Color(0xFFD32F2F)
                    else -> when (statusCodeText.toIntOrNull()) {
                        null -> metaColor
                        200 -> if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
                        else -> if (darkTheme) Color(0xFFFF8A80) else Color(0xFFC62828)
                    }
                }
            )
            Text(" ", fontSize = tab, color = metaColor)
            Text("$responseTimeText ", fontSize = tab, color = metaColor)
            if (responseSseEventCount.isNotBlank()) {
                val timingPart = buildString {
                    if (responseSseTtftText.isNotBlank()) append("  TTFT $responseSseTtftText")
                    if (responseSseTpotText.isNotBlank()) append("  TPOT $responseSseTpotText")
                }
                Text("$responseSizeText $responseSseEventCount$timingPart", fontSize = tab, color = metaColor)
            } else {
                Text(responseSizeText, fontSize = tab, color = metaColor)
            }
        }

        val iconTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
        val iconBtnMod = Modifier.size(32.dp)
        val iconMod = Modifier.size(17.dp)

        IconButton(onClick = onToggleSearch, modifier = iconBtnMod) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索响应正文",
                modifier = iconMod,
                tint = if (searchActive) MaterialTheme.colors.primary else iconTint,
            )
        }
        IconButton(onClick = onCopyResponseBody, enabled = copyResponseBodyEnabled, modifier = iconBtnMod) {
            Icon(
                imageVector = CustomIcons.ContentCopy,
                contentDescription = "复制响应正文到剪贴板",
                modifier = iconMod,
                tint = iconTint,
            )
        }
        IconButton(onClick = onClearResponseLogs, enabled = clearResponseLogsEnabled, modifier = iconBtnMod) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除响应与压测日志文件",
                modifier = iconMod,
                tint = iconTint,
            )
        }

        if (showMcpConnectionControls) {
            IconButton(onClick = onMcpConnect, enabled = mcpConnectEnabled, modifier = iconBtnMod) {
                Icon(
                    imageVector = CustomIcons.Link,
                    contentDescription = if (isMcpConnected) "MCP connected" else "Connect MCP",
                    modifier = iconMod,
                    tint = if (isMcpConnected) MaterialTheme.colors.primary else iconTint,
                )
            }
            IconButton(onClick = onMcpReconnect, enabled = mcpReconnectEnabled, modifier = iconBtnMod) {
                Icon(
                    imageVector = CustomIcons.Refresh,
                    contentDescription = "Reconnect MCP",
                    modifier = iconMod,
                    tint = iconTint,
                )
            }
            IconButton(onClick = onMcpDisconnect, enabled = mcpDisconnectEnabled, modifier = iconBtnMod) {
                Icon(
                    imageVector = CustomIcons.LinkOff,
                    contentDescription = "Disconnect MCP",
                    modifier = iconMod,
                    tint = iconTint,
                )
            }
        }
        if (showMcpCatalogRefresh) {
            IconButton(onClick = onRefreshMcpCatalog, enabled = mcpCatalogRefreshEnabled, modifier = iconBtnMod) {
                Icon(
                    imageVector = CustomIcons.LibraryBooks,
                    contentDescription = "Refresh MCP catalog",
                    modifier = iconMod,
                    tint = iconTint,
                )
            }
        }
        var historyMenuExpanded by remember { mutableStateOf(false) }
        androidx.compose.foundation.layout.Box {
            IconButton(
                onClick = { historyMenuExpanded = true },
                enabled = historyEntries.isNotEmpty(),
                modifier = iconBtnMod,
            ) {
                Icon(
                    imageVector = CustomIcons.AccessTime,
                    contentDescription = "选择历史响应",
                    modifier = iconMod,
                    tint = if (historyEntries.isNotEmpty()) iconTint else iconTint.copy(alpha = 0.3f),
                )
            }
            DropdownMenu(
                expanded = historyMenuExpanded,
                onDismissRequest = { historyMenuExpanded = false },
            ) {
                val latestEntry = historyEntries.firstOrNull()
                val isLatestSelected = selectedHistoryEpochMs == null ||
                        (latestEntry != null && selectedHistoryEpochMs == latestEntry.epochMs)
                DropdownMenuItem(
                    onClick = {
                        onHistorySelected(null)
                        historyMenuExpanded = false
                    },
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        "最新",
                        fontSize = tab,
                        color = if (isLatestSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                    )
                }
                if (historyEntries.isNotEmpty()) {
                    DropdownMenuItem(onClick = {}, enabled = false, modifier = Modifier.height(20.dp)) {
                        Divider()
                    }
                    for (entry in historyEntries) {
                        val isSelected = selectedHistoryEpochMs == entry.epochMs
                        DropdownMenuItem(
                            onClick = {
                                onHistorySelected(entry.epochMs)
                                historyMenuExpanded = false
                            },
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text(
                                entry.displayTime,
                                fontSize = tab,
                                color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
