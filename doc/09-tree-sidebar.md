# Compose 树形组件与侧边栏

本文介绍如何使用 LazyColumn 实现树形结构、展开收起与拖拽功能。

## 9.1 树形数据结构

### UI 模型

```kotlin
data class UiCollection(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val rootRequests: List<UiRequestSummary>,
)

data class UiFolder(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val requests: List<UiRequestSummary>,
)

data class UiRequestSummary(
    val id: String,
    val name: String,
    val method: String,
)
```

### TreeSelection 选择态

```kotlin
sealed class TreeSelection {
    data class Collection(val id: String) : TreeSelection()
    data class Folder(val id: String) : TreeSelection()
    data class Request(val id: String) : TreeSelection()
}
```

> 项目中的数据 (`src/main/kotlin/tree/CollectionModels.kt`):
```kotlin
data class UiCollection(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val rootRequests: List<UiRequestSummary>,
)

data class UiFolder(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val requests: List<UiRequestSummary>,
)

data class UiRequestSummary(
    val id: String,
    val name: String,
    val method: String,
)

sealed class TreeSelection {
    data class Collection(val id: String) : TreeSelection()
    data class Folder(val id: String) : TreeSelection()
    data class Request(val id: String) : TreeSelection()
}
```

## 9.2 LazyColumn 实现多级树

### 基础列表

```kotlin
@Composable
fun CollectionTreeSidebar(
    tree: List<UiCollection>,
    // ...
) {
    LazyColumn {
        items(
            items = tree,
            key = { it.id }
        ) { collection ->
            CollectionItem(collection)
        }
    }
}
```

### 递归渲染文件夹

```kotlin
@Composable
fun TreeNode(
    node: UiFolder,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (TreeSelection) -> Unit,
) {
    Column {
        // 展开/收起图标
        Row(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown
                else Icons.Filled.KeyboardArrowRight,
                null
            )
            Icon(
                if (expanded) Icons.Filled.FolderOpen
                else Icons.Filled.Folder,
                null
            )
            Text(node.name)
        }
        
        // 递归渲染子节点
        if (expanded) {
            node.folders.forEach { child ->
                TreeNode(child, expanded, onToggle, onSelect)
            }
            node.requests.forEach { req ->
                RequestItem(req, onSelect)
            }
        }
    }
}
```

> 项目中的实现 (`src/main/kotlin/tree/CollectionTreeSidebar.kt:330-410`):
```kotlin
@Composable
private fun TreeItemCollection(
    node: UiCollection,
    selectedNode: TreeSelection?,
    // ...
) {
    // 展开状态由外部控制
    val expanded = expandedCollectionIds.contains(node.id)
    
    Column {
        // Collection 行
        Row(
            modifier = Modifier.clickable { onToggleCollection(node.id) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown
                else Icons.Filled.KeyboardArrowRight,
                null
            )
            Icon(Icons.Filled.LibraryBooks, null)
            Text(node.name)
        }
        
        // 文件夹和请求
        if (expanded) {
            node.folders.forEach { folder ->
                TreeItemFolder(folder, selectedNode, expandedFolderIds, onToggleFolder, onSelectNode)
            }
            node.rootRequests.forEach { req ->
                TreeItemRequest(req, selectedNode, onSelectNode)
            }
        }
    }
}
```

## 9.3 展开/收起状态管理

### 状态存储

```kotlin
class TreeExpandPrefs {
    val collectionIds: Set<String> = emptySet()
    val folderIds: Set<String> = emptySet()
    
    fun toggleCollection(id: String) {
        collectionIds = if (id in collectionIds) collectionIds - id
        else collectionIds + id
    }
    
    fun toggleFolder(id: String) {
        folderIds = if (id in folderIds) folderIds - id
        else folderIds + id
    }
}
```

> 项目中的状态管理 (`src/main/kotlin/app/Main.kt:135-139`):
```kotlin
val expandLoaded = remember { TreeExpandPrefs.load() }
var expandedCollectionIds by remember { mutableStateOf(expandLoaded.collectionIds) }
var expandedFolderIds by remember { mutableStateOf(expandLoaded.folderIds) }
var persistTreeExpand by remember { mutableStateOf(expandLoaded.fromSavedFile) }

fun refreshTree() {
    tree = repository.loadTree()
}
```

## 9.4 ContextMenu 右键菜单

```kotlin
@Composable
fun TreeItemRequest(
    req: UiRequestSummary,
    onSelect: (TreeSelection) -> Unit,
) {
    ContextMenuArea {
        ContextMenuItem("重命名") { /* ... */ }
        ContextMenuItem("复制") { /* ... */ }
        ContextMenuItem("删除") { /* ... */ }
    }
    
    Row(
        modifier = Modifier.clickable { onSelect(TreeSelection.Request(req.id)) }
    ) {
        Text(req.method, color = methodColor(req.method))
        Text(req.name)
    }
}
```

> 项目中的右键菜单 (`src/main/kotlin/tree/CollectionTreeSidebar.kt`):
```kotlin
ContextMenuArea {
    itemsProvider = {
        listOfNotNull(
            ContextMenuItem("重命名") { /* ... */ },
            ContextMenuItem("新建文件夹") { /* ... */ },
            ContextMenuItem("新建请求") { /* ... */ },
            ContextMenuItem("导出为 cURL") { /* ... */ },
            // ...
        )
    }
}
```

## 9.5 拖拽处理

### 检测拖拽

```kotlin
Modifier.pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        // 开始拖拽
    }
}
```

### 放置目标

```kotlin
@Composable
fun TreeDropGap(
    active: Boolean,
    zoneKey: String,
    target: TreeDropTarget,
    dropRegistry: DropZoneRegistry,
    hovered: TreeDropTarget?,
) {
    val highlight = active && hovered == target
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (active) 4.dp else 0.dp)
            .background(
                when {
                    !active -> Color.Transparent
                    highlight -> MaterialTheme.colors.primary.copy(alpha = 0.38f)
                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.07f)
                }
            )
    )
}
```

> 项目中的拖拽实现 (`src/main/kotlin/tree/CollectionTreeSidebar.kt:103-147`):
```kotlin
private fun bestDropTarget(
    zones: Map<String, Pair<Rect, TreeDropTarget>>,
    point: Offset,
): TreeDropTarget? {
    return zones.values
        .filter { it.first.contains(point) }
        .minByOrNull { it.first.width * it.first.height }
        ?.second
}

@Composable
private fun TreeDropGap(
    active: Boolean,
    zoneKey: String,
    target: TreeDropTarget,
    dropRegistry: DropZoneRegistry,
    hovered: TreeDropTarget?,
) {
    val highlight = active && hovered == target
    // ...
}
```

## 9.6 滚动与定位

### 滚动到指定项

```kotlin
val listState = rememberLazyListState()

LaunchedEffect(scrollToItem) {
    listState.animateScrollToItem(index = targetIndex)
}
```

### 让列表项可见

```kotlin
@Composable
fun LazyListEnsureVisible(
    listState: LazyListState,
    itemIndex: Int,
) {
    LaunchedEffect(itemIndex) {
        listState.animateScrollToItem(index = itemIndex)
    }
}
```

> 项目中的滚动 (`src/main/kotlin/app/LazyListEnsureVisible.kt`):
```kotlin
@Composable
fun LazyListEnsureVisible(
    listState: LazyListState,
    index: Int,
) {
    LaunchedEffect(index) {
        listState.animateScrollToItem(index = index)
    }
}
```

## 9.7 总结

| 功能 | 实现要点 |
|------|---------|
| 树形结构 | 递归 Composable 渲染 |
| 展开/收起 | `expandedIds: Set<String>` 状态管理 |
| 右键菜单 | `ContextMenuArea` |
| 拖拽 | `detectDragGestures` + 放置区域检测 |
| 滚动定位 | `LazyListState.animateScrollToItem` |

**下篇**：对话框与全局搜索