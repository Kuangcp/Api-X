# 桌面应用快捷键绑定

本文介绍 Compose Desktop 中的快捷键系统与实现。

## 12.1 KeyEvent 监听基础

### onPreviewKeyEvent

```kotlin
Modifier.onPreviewKeyEvent { event ->
    when {
        event.type == KeyEventType.KeyDown && event.key == Key.A -> {
            // 处理 A 键按下
            true
        }
        event.type == KeyEventType.KeyUp && event.key == Key.A -> {
            // 处理 A 键释放
            true
        }
        else -> false
    }
}
```

### 修饰符链式调用

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .onPreviewKeyEvent { ev ->
            when {
                ev.type == KeyEventType.KeyDown && ev.key == Key.S &&
                        ev.isCtrlPressed -> {
                    save()
                    true
                }
                else -> false
            }
        }
) {
    // 内容
}
```

## 12.2 常用快捷键

### Ctrl+S 保存

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.S) {
        save()
        true
    } else {
        false
    }
}
```

### Ctrl+N 新建

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.N) {
        createNew()
        true
    } else {
        false
    }
}
```

### Escape 关闭

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) {
        dismiss()
        true
    } else {
        false
    }
}
```

> 项目中的快捷键 (`src/main/kotlin/app/GlobalSearchDialog.kt:144-150`):
```kotlin
modifier = Modifier
    .fillMaxSize()
    .padding(16.dp)
    .onPreviewKeyEvent { ev ->
        when {
            ev.type == KeyEventType.KeyDown && ev.key == Key.Escape -> {
                onCloseRequest()
                true
            }
            else -> false
        }
    }
```

## 12.3 Ctrl+K 全局搜索

### 快捷键定义

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

### 打开搜索对话框

```kotlin
if (showGlobalSearch) {
    GlobalSearchDialogWindow(
        visible = true,
        onClose = { showGlobalSearch = false },
        onPickRequest = { id ->
            applyRequestToEditor(id)
            showGlobalSearch = false
        },
    )
}
```

> 项目中的 Ctrl+K (`src/main/kotlin/app/Main.kt:34-42`):
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

## 12.4 Ctrl+Tab 切换最近请求

### 快捷键判断

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.Tab) {
        if (recentSwitcherIds.isNotEmpty()) {
            recentSwitcherActive = true
            if (ev.isShiftPressed) {
                // Shift+Tab: 上一个
                recentSwitcherIndex = (recentSwitcherIndex - 1).coerceAtLeast(0)
            } else {
                // Tab: 下一个
                recentSwitcherIndex = (recentSwitcherIndex + 1).coerceAtMost(recentSwitcherIds.lastIndex)
            }
        }
        true
    } else {
        false
    }
}
```

### 组合键条件

- `ev.isCtrlPressed` - Ctrl 键按下
- `ev.isMetaPressed` - Command 键 (macOS)
- `ev.isShiftPressed` - Shift 键按下

> 项目中的 Ctrl+Tab (`src/main/kotlin/app/Main.kt:293-305`):
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

## 12.5 冲突解决

### 优先级处理

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    when {
        // 优先处理特定组合
        ev.type == KeyEventType.KeyDown && ev.key == Key.F1 -> {
            showHelp()
            true
        }
        // 然后处理单键
        ev.type == KeyEventType.KeyDown && ev.key == Key.F1 -> {
            showHelp()
            true
        }
        else -> false
    }
}
```

### 阻止默认行为

```kotlin
Modifier.onPreviewKeyEvent { ev ->
    if (ev.type == KeyEventType.KeyDown) {
        when (ev.key) {
            Key.Tab -> {
                // 阻止默认 Tab 焦点切换
                customTabHandling()
                true
            }
            else -> false
        }
    } else {
        false
    }
}
```

## 12.6 全局快捷键

### Window 级别快捷键

对于需要全局捕获的快捷键（如 Ctrl+W 关闭窗口），在 Window 级别处理：

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.keyEvent

Window(
    onPreviewKeyEvent = { event ->
        // 窗口级别处理
        handleWindowKeyEvent(event)
    }
) {
    // 内容
}
```

### 快捷键帮助显示

```kotlin
@Composable
fun HelpDialog() {
    Column {
        Text("快捷键：")
        Row { Text("Ctrl+K ") }
        Row { Text("Ctrl+S ") }
        Row { Text("Ctrl+Tab ") }
        Row { Text("Escape ") }
    }
}
```

## 12.7 RecentRequest Switcher Overlay

### 覆盖层显示

```kotlin
if (recentSwitcherActive) {
    RecentRequestSwitcherOverlay(
        visible = true,
        requestIds = recentSwitcherIds,
        onSelect = { id ->
            applyRequestToEditor(id)
            recentSwitcherActive = false
        },
        onDismiss = { recentSwitcherActive = false }
    )
}
```

### 选择逻辑

```kotlin
@Composable
fun RecentRequestSwitcherOverlay(
    visible: Boolean,
    requestIds: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier.align(Alignment.Center).width(400.dp)
        ) {
            LazyColumn {
                itemsIndexed(requestIds) { index, id ->
                    Row(
                        modifier = Modifier.clickable { onSelect(id) }
                    ) {
                        Text("${index + 1}. ")
                        Text(id)
                    }
                }
            }
        }
    }
}
```

> 项目中的实现 (`src/main/kotlin/app/RecentRequestSwitcherOverlay.kt`):
```kotlin
@Composable
fun RecentRequestSwitcherOverlay(
    visible: Boolean,
    requestIds: List<String>,
    currentRequestId: String?,
    getRequestName: (String) -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .width(360.dp)
        ) {
            // ...
        }
    }
}
```

## 12.8 总结

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+K` | 全局搜索 |
| `Ctrl+Tab` | 切换最近请求 |
| `Ctrl+S` | 保存 |
| `Ctrl+N` | 新建 |
| `Escape` | 关闭/取消 |
| `Enter` | 确认 |
| `↑/↓` | 上下选择 |

第三章已完成！包含 4 篇博客：
- 博客 9：树形组件与侧边栏
- 博客 10：对话框与全局搜索
- 博客 11：主题系统与动态配色
- 博客 12：桌面应用快捷键绑定

需要继续生成第四章吗？