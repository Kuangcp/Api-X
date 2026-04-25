# Compose 布局基础与 Material Design

本文介绍 Compose 的布局组件和主题系统。

## 3.1 基础布局组件：Row / Column / Box

Compose 提供三个基础布局组件：

### Row：水平排列
```kotlin
Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("左侧")
    Text("右侧")
}
```

### Column：垂直排列
```kotlin
Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text("第一行")
    Text("第二行")
}
```

### Box：层叠布局
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Image(...)
    Text("覆盖在图片上", modifier = Modifier.align(Alignment.BottomStart))
}
```

> 项目中的实际布局 (`src/main/kotlin/app/Main.kt:475-490`):
```kotlin
Row(
    Modifier.fillMaxWidth().height(48.dp)
        .background(MaterialTheme.colors.background)
) {
    if (treeSidebarVisible) {
        Box(
            Modifier.fillMaxHeight().width((contentWidthPx * treeSplitRatio).dp)
        ) {
            CollectionTreeSidebar(
                // ...
            )
        }
        Box(
            Modifier.fillMaxHeight().width(1.dp).background(dividerColor)
        )
    }
    // 右侧请求编辑区
    Box(Modifier.fillMaxSize()) {
        // ...
    }
}
```

## 3.2 Modifier 修饰符链

修饰符是 Compose 的核心概念，通过链式调用组合样式和行为：

```kotlin
Text(
    text = "Hello",
    modifier = Modifier
        .fillMaxWidth()           // 宽度撑满
        .padding(16.dp)         // 内边距
        .background(Color.Blue)  // 背景色
        .clickable { /* */ }     // 点击事件
)
```

常见修饰符：
- `fillMaxSize()` / `fillMaxWidth()` / `fillMaxHeight()`
- `wrapContentSize()`
- `size(100.dp)`
- `padding(margin)` / `padding(start/end/top/bottom)`
- `background(color)`
- `clickable { }`

## 3.3 LazyColumn 高效列表

对于长列表，必须使用 `LazyColumn`（仅渲染可见项）：

```kotlin
LazyColumn {
    items(list) { item ->
        ListItem(item)
    }
}
```

带 key 维持状态：
```kotlin
items(
    items = requests,
    key = { it.id }
) { request ->
    RequestItem(request)
}
```

> 项目中的树形列表 (`src/main/kotlin/tree/CollectionTreeSidebar.kt`):
```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize()
) {
    items(
        items = tree,
        key = { it.id }
    ) { node ->
        when (node) {
            is TreeItem.Collection -> CollectionItem(...)
            is TreeItem.Folder -> FolderItem(...)
            is TreeItem.Request -> RequestItem(...)
        }
    }
}
```

## 3.4 Material Design 3 主题

Compose 默认支持 Material3：

```kotlin
MaterialTheme(
    colorScheme = ColorScheme,
    typography = Typography,
    content = content
)
```

### 颜色方案

```kotlin
val darkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF121212),
    // ...
)

val lightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFFFFFFF),
    // ...
)
```

### 主题切换

```kotlin
var isDarkTheme by remember { mutableStateOf(true) }

MaterialTheme(
    colors = if (isDarkTheme) darkColors() else lightColors()
) {
    // 你的 UI
}
```

> 项目中的主题切换 (`src/main/kotlin/app/Main.kt:293, 293-297`):
```kotlin
var isDarkTheme by remember { mutableStateOf(true) }
// ...
MaterialTheme(
    colors = if (isDarkTheme) darkColors() else lightColors()
) {
    // 布局内容
}
```

## 3.5 Material3 色彩体系

Material3 使用更丰富的颜色角色：

```kotlin
import androidx.compose.material3.*

val colorScheme = darkColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    secondary = md_theme_light_secondary,
    // ...
)
```

项目中定义的颜色 (`src/main/kotlin/app/Main.kt` 中的引用):
```kotlin
MaterialTheme(
    colors = if (isDarkTheme) darkColors() else lightColors()
) {
    Button(
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
        )
    ) { }
}
```

## 3.6 图标与字体

### Material Icons

```kotlin
import androidx.compose.material.icons.*

Icon(
    imageVector = Icons.Filled.Search,
    contentDescription = "搜索"
)

Icon(
    imageVector = Icons.Default.Settings,
    contentDescription = "设置"
)
```

扩展图标库（项目依赖）：
```kotlin
implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
```

> 项目中的图标使用 (`src/main/kotlin/http/RequestPanel.kt`):
```kotlin
IconButton(onClick = onSend) {
    Icon(
        imageVector = Icons.Filled.Send,
        contentDescription = "发送请求"
    )
}
```

### Typography 字体样式

```kotlin
val Typography = Typography(
    body1 = TextStyle(fontFamily = FontFamily.Default),
    h1 = TextStyle(fontSize = 24.sp)
)

Text(
    text = "标题",
    style = MaterialTheme.typography.h1
)
```

## 3.7 Surface 与容器

```kotlin
Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colors.background
) {
    // 内容
}
```

带阴影的卡片：
```kotlin
Card(
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
) {
    Text("卡片内容")
}
```

## 3.8 响应式分割布局

项目中的分割布局实现 (`src/main/kotlin/app/Main.kt:475-510`):
```kotlin
Row(
    Modifier.fillMaxWidth().height(48.dp)
        .background(MaterialTheme.colors.background)
) {
    if (treeSidebarVisible) {
        Box(
            // 左侧树：宽度比例
            Modifier.fillMaxHeight()
                .width((contentWidthPx * treeSplitRatio).dp)
        ) {
            CollectionTreeSidebar(...)
        }
        // 分隔线
        Box(
            Modifier.fillMaxHeight().width(1.dp)
                .background(dividerColor)
        )
    }
    // 右侧主区域
    Box(Modifier.fillMaxSize()) {
        // ...
    }
}
```

## 3.9 LazyListState 滚动控制

控制 LazyColumn 滚动位置：

```kotlin
val listState = rememberLazyListState()

// 滚动到指定位置
LaunchedEffect(scrollToItem) {
    listState.animateScrollToItem(index = targetIndex)
}
```

> 项目中的列表状态 (`src/main/kotlin/app/Main.kt:291`):
```kotlin
val responseListState = remember(responseScopeKey) { LazyListState() }
```

## 总结

| 组件 | 用途 |
|------|------|
| `Row` | 水平排列 |
| `Column` | 垂直排列 |
| `Box` | 层叠布局 |
| `LazyColumn` | 高性能长列表 |
| `Modifier` | 样式与行为链 |
| `MaterialTheme` | Material3 主题 |
| `Surface` / `Card` | 容器组件 |

本篇介绍了 Compose 布局基础，下一篇我们将学习实际项目中的 HTTP 请求处理。