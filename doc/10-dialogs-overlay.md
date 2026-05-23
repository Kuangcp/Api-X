# 对话框与全局搜索

本文介绍 Compose 中的对话框、全局搜索实现与 RecentRequest 快速切换。

## 10.1 对话框窗口管理

项目使用统一的 `ui/Dialogs.kt` 编排所有对话框（Settings / Environment Manager / Global Search / Collection Settings）：

```kotlin
@Composable
fun Dialogs(
    showSettings: Boolean,
    showEnvironmentManager: Boolean,
    showGlobalSearch: Boolean,
    showCollectionSettings: Boolean,
    collectionSettingsTarget: TreeSelection?,
    // ...
) {
    if (showSettings) SettingsDialogWindow(...)
    if (showEnvironmentManager) EnvironmentManagerDialogWindow(...)
    if (showGlobalSearch) GlobalSearchDialogWindow(...)
    if (showCollectionSettings) CollectionSettingsDialog(...)
}
```

### DialogWindow 独立窗口

对于全局搜索等复杂 UI，使用独立窗口：

```kotlin
@Composable
fun GlobalSearchDialogWindow(
    visible: Boolean,
    onClose: () -> Unit,
    onPickRequest: (String) -> Unit,
) {
    if (!visible) return
    
    DialogWindow(
        onCloseRequest = onClose,
        title = "全局搜索",
        state = rememberDialogState(width = 560.dp, height = 480.dp),
    ) {
        GlobalSearchDialogBody(onClose, onPickRequest)
    }
}
```

> 项目中的实现 (`src/main/kotlin/app/GlobalSearchDialog.kt`):
```kotlin
@Composable
fun GlobalSearchDialogWindow(
    visible: Boolean,
    isDarkTheme: Boolean,
    appBackgroundHex: String,
    typographyBase: Typography,
    tree: List<UiCollection>,
    repository: CollectionRepository,
    onCloseRequest: () -> Unit,
    onPickRequest: (String) -> Unit,
) {
    if (!visible) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "全局搜索",
        state = rememberDialogState(width = 560.dp, height = 480.dp),
    ) {
        val colors = appMaterialColors(isDarkTheme, appBackgroundHex)
        MaterialTheme(colors = colors, typography = typographyBase) {
            GlobalSearchDialogBody(
                tree = tree, repository = repository,
                onCloseRequest = onCloseRequest, onPickRequest = onPickRequest,
            )
        }
    }
}
```

### CollectionSettingsDialog

集合和文件夹级别的 Auth 配置对话框 (`src/main/kotlin/app/CollectionSettingsDialog.kt`)：

```kotlin
@Composable
fun CollectionSettingsDialog(
    visible: Boolean,
    target: TreeSelection?,  // Collection 或 Folder
    repository: CollectionRepository,
    onCloseRequest: () -> Unit,
) {
    if (!visible || target == null) return
    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "集合设置",
        state = rememberDialogState(width = 480.dp, height = 320.dp),
    ) {
        // Auth 编辑器用于整个集合/文件夹的默认认证
        AuthEditor(auth = currentAuth, onAuthChange = { saveAuth(it) }, ...)
    }
}
```

## 10.2 全局搜索实现

### 搜索状态管理

```kotlin
@Composable
fun GlobalSearchDialogBody(
    repository: CollectionRepository,
    onClose: () -> Unit,
    onPickRequest: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<Request>>(emptyList()) }
    var selectedResultIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    
    // 加载数据
    LaunchedEffect(tree) {
        rows = repository.loadAllRequestsForGlobalSearch()
    }
    
    // 过滤
    val filtered by remember(rows, query) {
        derivedStateOf {
            val q = query.trim()
            if (q.isEmpty()) emptyList()
            else rows.filter {
                matchesGlobalSearchQuery(q, it.name, it.url, it.headersText, it.bodyText)
            }
        }
    }
}
```

### 搜索匹配逻辑

```kotlin
fun matchesGlobalSearchQuery(
    query: String,
    name: String,
    url: String,
    headers: String,
    body: String,
): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
            url.lowercase().contains(q) ||
            headers.lowercase().contains(q) ||
            body.lowercase().contains(q)
}
```

### 选中状态与键盘导航

```kotlin
LaunchedEffect(selectedResultIndex, filtered.size) {
    if (selectedResultIndex < 0 || filtered.isEmpty()) return@LaunchedEffect
    val idx = selectedResultIndex.coerceIn(0, filtered.lastIndex)
    listState.ensureHighlightVisible(idx, defaultRowHeightPx)
}

Column {
    // 搜索输入框
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth()
    )
    
    // 结果列表
    LazyColumn(state = listState) {
        itemsIndexed(filtered) { index, item ->
            val selected = index == selectedResultIndex
            Row(
                modifier = Modifier
                    .background(if (selected) highlightColor else Color.Transparent)
                    .clickable { onPickRequest(item.id) }
            ) {
                Text(item.method)
                Text(item.name)
                Text(item.url)
            }
        }
    }
}
```

> 项目中的搜索 (`src/main/kotlin/app/GlobalSearchDialog.kt:111-137`):
```kotlin
val filtered by remember(rows, query) {
    derivedStateOf {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else rows.filter {
            matchesGlobalSearchQuery(q, it.name, it.url, it.headersText, it.bodyText)
        }
    }
}

LaunchedEffect(query) {
    selectedResultIndex = -1
}

LaunchedEffect(filtered) {
    if (filtered.isEmpty()) {
        selectedResultIndex = -1
    } else if (selectedResultIndex >= filtered.size) {
        selectedResultIndex = filtered.lastIndex
    }
}

LaunchedEffect(selectedResultIndex, filtered.size) {
    if (selectedResultIndex < 0 || filtered.isEmpty()) return@LaunchedEffect
    val idx = selectedResultIndex.coerceIn(0, filtered.lastIndex)
    listState.ensureHighlightVisible(idx, defaultRowHeightPx)
}
```

## 10.3 Ctrl+K 全局快捷键

### 键盘事件监听

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.K) {
        showGlobalSearch = true
        true
    } else {
        false
    }
}
```

### 主窗口中的快捷键

> 项目中的快捷键 (`src/main/kotlin/app/Main.kt:34-39`):
```kotlin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.K) {
        showGlobalSearch = true
        true
    } else {
        false
    }
}
```

### 搜索框内快捷键

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .onPreviewKeyEvent { ev ->
            when {
                ev.type == KeyEventType.KeyDown && ev.key == Key.Escape -> {
                    onCloseRequest()
                    true
                }
                ev.type == KeyEventType.KeyDown && ev.key == Key.Enter -> {
                    if (selectedResultIndex >= 0) {
                        onPickRequest(filtered[selectedResultIndex].id)
                    }
                    true
                }
                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                    selectedResultIndex = (selectedResultIndex + 1).coerceAtMost(filtered.lastIndex)
                    true
                }
                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp -> {
                    selectedResultIndex = (selectedResultIndex - 1).coerceAtLeast(0)
                    true
                }
                else -> false
            }
        }
) {
    // ...
}
```

> 项目中的搜索框快捷键 (`src/main/kotlin/app/GlobalSearchDialog.kt:144-170`):
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .onPreviewKeyEvent { ev ->
            when {
                ev.type == KeyEventType.KeyDown && ev.key == Key.Escape -> {
                    onCloseRequest()
                    true
                }
                ev.type == KeyEventType.KeyDown && ev.key == Key.Enter -> {
                    if (selectedResultIndex >= 0) {
                        onPickRequest(filtered[selectedResultIndex].id)
                    }
                    true
                }
                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown -> {
                    selectedResultIndex = (selectedResultIndex + 1).coerceAtMost(filtered.lastIndex)
                    true
                }
                ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp -> {
                    selectedResultIndex = (selectedResultIndex - 1).coerceAtLeast(0)
                    true
                }
                else -> false
            }
        }
) {
    // ...
}
```

## 10.4 RecentRequest 快速切换

### 最近请求存储

```kotlin
object RecentRequestUsageStore {
    private val file = File(appData, "recent_requests.json")
    
    fun touch(requestId: String) {
        val order = load().toMutableMap()
        order[requestId] = System.currentTimeMillis()
        save(order)
    }
    
    fun orderedIdsNewestFirst(): List<String> {
        return load().entries.sortedByDescending { it.value }.map { it.key }
    }
}
```

### RecentRequestSwitcherOverlay

```kotlin
@Composable
fun RecentRequestSwitcherOverlay(
    visible: Boolean,
    requestIds: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    
    // 覆盖层 UI
}
```

> 项目中的实现 (`src/main/kotlin/app/RecentRequestSwitcherOverlay.kt`): 使用 AWTEventListener 实现 Ctrl+Tab 的按下-保持-释放交互模式。

```kotlin
@Composable
fun RecentRequestSwitcherOverlay(
    requestIds: List<String>,
    highlightIndex: Int,
    tree: List<UiCollection>,
    repository: CollectionRepository,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Card(
            modifier = Modifier.align(Alignment.Center).width(360.dp)
        ) {
            LazyColumn {
                itemsIndexed(requestIds) { index, id ->
                    val name = requestLocationLabel(tree, id) ?: id
                    Row(
                        modifier = Modifier.background(
                            if (index == highlightIndex) highlightColor
                            else Color.Transparent
                        )
                    ) {
                        Text("${index + 1}. $name")
                    }
                }
            }
        }
    }
}
```

Ctrl+Tab 的交互流程（`src/main/kotlin/app/Main.kt:162-205`）：
1. **按下 Ctrl+Tab** - 激活 switcher（AWTEventListener 捕获）
2. **按住 Ctrl 时按 Tab/Shift+Tab** - 在列表中选择上下项
3. **释放 Ctrl** - 提交选择并关闭 switcher
4. **按 Escape** - 取消并关闭

## 10.5 Ctrl+Tab 切换

### 主窗口快捷键

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    // Ctrl+Tab 切换最近请求
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.Tab) {
        if (ev.isShiftPressed) {
            // Shift+Tab: 切换到上一个
            recentSwitcherIndex = (recentSwitcherIndex - 1).coerceAtLeast(0)
        } else {
            // Tab: 切换到下一个
            recentSwitcherIndex = (recentSwitcherIndex + 1).coerceAtMost(recentSwitcherIds.lastIndex)
        }
        true
    } else {
        false
    }
}
```

> 项目中的 Ctrl+Tab 实现 (`src/main/kotlin/app/Main.kt:293-305`):
```kotlin
var recentSwitcherActive by remember { mutableStateOf(false) }
var recentSwitcherIds by remember { mutableStateOf<List<String>>(emptyList()) }
var recentSwitcherIndex by remember { mutableStateOf(0) }

Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.Tab) {
        if (recentSwitcherIds.isNotEmpty()) {
            recentSwitcherActive = true
            if (ev.isShiftPressed) {
                recentSwitcherIndex = (recentSwitcherIndex - 1).coerceAtLeast(0)
            } else {
                recentSwitcherIndex = (recentSwitcherIndex + 1).coerceAtMost(recentSwitcherIds.lastIndex)
            }
        }
        true
    } else {
        false
    }
}
```

## 10.6 总结

| 功能 | 实现要点 |
|------|---------|
| Dialog | `Dialog` / `DialogWindow` |
| 全局搜索 | `derivedStateOf` 过滤 |
| Ctrl+K | `onPreviewKeyEvent` |
| 键盘导航 | Enter/Direction 键 |
| RecentRequest | `touch()` 记录使用时间 |
| Ctrl+Tab | 最近请求列表切换 |

**下篇**：Compose 主题系统与动态配色