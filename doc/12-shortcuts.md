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

> 项目中的 Ctrl+K (`src/main/kotlin/app/Main.kt:220-228`):
```kotlin
Window(
    onPreviewKeyEvent = { event ->
        if (vm.recentSwitcherActive) {
            true  // 按住 Ctrl 时禁用其他快捷键
        } else {
            if (event.type == KeyEventType.KeyDown) {
                when {
                    (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K ->
                        { vm.setShowGlobalSearch(true); true }
                    event.isCtrlPressed && event.key == Key.Enter ->
                        { vm.onStartRequest(); true }
                    event.key == Key.Escape ->
                        { if (vm.isLoading) { vm.onCancelRequest(); true } else false }
                    else -> false
                }
            } else false
        }
    }
)
```

## 12.4 Ctrl+Tab 切换最近请求

由于 Compose Desktop 的 `onPreviewKeyEvent` 无法捕获 Tab 键（被 AWT 焦点系统拦截），项目使用 **AWTEventListener** 在全局捕获快捷键：

### 交互流程

```kotlin
DisposableEffect(vm.recentSwitcherActive, vm.recentSwitcherIds, vm.recentSwitcherIndex) {
    val listener = AWTEventListener { raw ->
        val keyEvent = raw as? KeyEvent ?: return@AWTEventListener
        
        if (!vm.recentSwitcherActive) {
            // Ctrl+Tab 按下 → 激活 switcher
            if (keyEvent.id == KeyEvent.KEY_PRESSED &&
                keyEvent.keyCode == KeyEvent.VK_TAB && keyEvent.isControlDown) {
                activateRecentSwitcher(forward = !keyEvent.isShiftDown)
                keyEvent.consume()
            }
            return@AWTEventListener
        }
        
        when (keyEvent.id) {
            KeyEvent.KEY_PRESSED -> {
                when {
                    keyEvent.keyCode == KeyEvent.VK_ESCAPE -> { vm.setRecentSwitcherActive(false); keyEvent.consume() }
                    keyEvent.keyCode == KeyEvent.VK_TAB -> { cycleRecentSwitcher(!keyEvent.isShiftDown); keyEvent.consume() }
                }
            }
            KeyEvent.KEY_RELEASED -> {
                if (keyEvent.keyCode == KeyEvent.VK_CONTROL || !keyEvent.isControlDown) {
                    commitRecentSwitcherSelectionAndClose()  // 释放 Ctrl 时提交选择
                    keyEvent.consume()
                }
            }
        }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
    onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
}
```

### 组合键条件

- `keyEvent.isControlDown` - Ctrl 键按下
- `keyEvent.isShiftDown` - Shift 键按下
- 使用 `AWTEvent.KEY_EVENT_MASK` 过滤键盘事件

### 切换逻辑

```kotlin
fun cycleRecentSwitcher(forward: Boolean) {
    val list = vm.recentSwitcherIds
    val next = if (forward) {
        (vm.recentSwitcherIndex + 1) % list.size
    } else {
        (vm.recentSwitcherIndex - 1 + list.size) % list.size
    }
    vm.setRecentSwitcherIndex(next)
}
```

### 快捷键对照表

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+K` / `Cmd+K` | 全局搜索 |
| `Ctrl+Enter` | 发送请求 |
| `Ctrl+Tab` 按下 | 激活最近请求 switcher |
| `Tab` / `Shift+Tab`（switcher 激活时） | 上/下选择 |
| `Escape` | 取消请求 / 关闭 switcher |

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
| `Ctrl+K` / `Cmd+K` | 全局搜索 |
| `Ctrl+Enter` | 发送请求 |
| `Ctrl+Tab` | 激活最近请求切换 |
| `Tab/Shift+Tab`（switcher 中） | 切换选择 |
| `Ctrl` 释放（switcher 中） | 提交选择 |
| `Escape` | 关闭/取消 |

第三章已完成！包含 4 篇博客：
- 博客 9：树形组件与侧边栏
- 博客 10：对话框与全局搜索
- 博客 11：主题系统与动态配色
- 博客 12：桌面应用快捷键绑定