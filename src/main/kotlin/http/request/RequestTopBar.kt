package http.request

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import app.EnvironmentsState
/**
 * 顶栏环境下拉菜单项内边距。
 * 注意：[DropdownMenuItem] 内部固定 `minHeight`（约 48.dp），仅改 `contentPadding` 无法压低行高；
 * 环境列表改用 [EnvironmentDropdownMenuItem]。
 */
private val EnvironmentDropdownMenuItemPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
@Composable
private fun EnvironmentDropdownMenuItem(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(EnvironmentDropdownMenuItemPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 顶栏：左侧切换请求树、导入、右侧设置/主题/环境（全宽；与下方左右分栏组成 T 形布局） */
@Composable
fun WindowScope.RequestTopBar(
    isLoading: Boolean,
    isDarkTheme: Boolean,
    treeSidebarVisible: Boolean,
    onTreeSidebarToggle: () -> Unit,
    environmentsState: EnvironmentsState,
    onActiveEnvironmentChange: (String?) -> Unit,
    onManageEnvironmentsClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    mainWindowState: WindowState,
    onWindowCloseRequest: () -> Unit,
    onImportCollectionClick: () -> Unit,
    onImportCurlClick: () -> Unit,
    onPushDataClick: () -> Unit = {},
    onPullDataClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val topBarIconTint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
    val topBarIconButtonModifier = Modifier
        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
        .size(26.dp)
    val topBarIconModifier = Modifier.size(17.dp)
    var envMenuExpanded by remember { mutableStateOf(false) }
    val activeLabel = environmentsState.activeEnvironment()?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: "无环境"
    val envButtonText = "环境: $activeLabel"

    val isWindowMaximized = mainWindowState.placement == WindowPlacement.Maximized ||
        mainWindowState.placement == WindowPlacement.Fullscreen

    /** 与窗口控制按钮同一行、同一高度带，避免左侧控件与右侧「最小化/最大化/关闭」视觉错位 */
    val topBarHeight = 36.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowDraggableArea(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onTreeSidebarToggle,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier,
                    ) {
                        Icon(
                            imageVector = if (treeSidebarVisible) {
                                Icons.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Filled.KeyboardArrowRight
                            },
                            contentDescription = if (treeSidebarVisible) "隐藏请求树" else "显示请求树",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint,
                        )
                    }
                    IconButton(
                        onClick = onImportCollectionClick,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LibraryAdd,
                            contentDescription = "导入 Postman Collection…",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint
                        )
                    }
                    IconButton(
                        onClick = onImportCurlClick,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "从剪贴板导入 cURL",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint
                        )
                    }
                    IconButton(
                        onClick = onPushDataClick,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = "同步到 data 目录（供 Git 管理）",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint
                        )
                    }
                    IconButton(
                        onClick = onPullDataClick,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "从 data 目录合并到本地",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onSettingsClick,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint
                        )
                    }
                    IconButton(
                        onClick = onThemeToggle,
                        enabled = !isLoading,
                        modifier = topBarIconButtonModifier
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDarkTheme) "切换浅色主题" else "切换深色主题",
                            modifier = topBarIconModifier,
                            tint = topBarIconTint
                        )
                    }
                    Box {
                        TextButton(
                            onClick = { envMenuExpanded = true },
                            enabled = !isLoading,
                            modifier = Modifier.widthIn(max = 200.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    envButtonText,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high),
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = topBarIconTint,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = envMenuExpanded,
                            onDismissRequest = { envMenuExpanded = false },
                        ) {
                            EnvironmentDropdownMenuItem(
                                onClick = {
                                    onActiveEnvironmentChange(null)
                                    envMenuExpanded = false
                                },
                            ) {
                                Text(
                                    "无环境",
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 1,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high),
                                )
                            }
                            if (environmentsState.environments.isNotEmpty()) {
                                Divider()
                                for (env in environmentsState.environments) {
                                    EnvironmentDropdownMenuItem(
                                        onClick = {
                                            onActiveEnvironmentChange(env.id)
                                            envMenuExpanded = false
                                        },
                                    ) {
                                        Text(
                                            env.name.ifBlank { "(未命名)" },
                                            style = MaterialTheme.typography.body2,
                                            maxLines = 1,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high),
                                        )
                                    }
                                }
                            }
                            Divider()
                            EnvironmentDropdownMenuItem(
                                onClick = {
                                    envMenuExpanded = false
                                    onManageEnvironmentsClick()
                                },
                            ) {
                                Text(
                                    "管理环境…",
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 1,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.high),
                                )
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = { mainWindowState.isMinimized = true },
                enabled = !isLoading,
                modifier = topBarIconButtonModifier,
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "最小化",
                    modifier = topBarIconModifier,
                    tint = topBarIconTint,
                )
            }
            IconButton(
                onClick = {
                    mainWindowState.placement = if (isWindowMaximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                enabled = !isLoading,
                modifier = topBarIconButtonModifier,
            ) {
                Icon(
                    imageVector = if (isWindowMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                    contentDescription = if (isWindowMaximized) "还原" else "最大化",
                    modifier = topBarIconModifier,
                    tint = topBarIconTint,
                )
            }
            IconButton(
                onClick = onWindowCloseRequest,
                enabled = !isLoading,
                modifier = topBarIconButtonModifier,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    modifier = topBarIconModifier,
                    tint = topBarIconTint,
                )
            }
        }
        Divider(
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        )
    }
}
