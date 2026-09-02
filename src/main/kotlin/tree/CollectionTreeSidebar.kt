package tree

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import app.ui.CustomIcons
import app.ui.parseHexColorOrNull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import mcp.McpCatalogSummary
import mcp.McpPromptSummary
import mcp.McpResourceSummary
import mcp.McpToolSummary


import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private enum class DropIndicator { None, InsertBefore, InsertAfter, Into }

private class DropZoneRegistry {
    val zones = mutableStateMapOf<String, Pair<Rect, RowDropDesc>>()

    fun sync(key: String, bounds: Rect, desc: RowDropDesc) {
        zones[key] = bounds to desc
    }

    fun removeKey(key: String) {
        zones.remove(key)
    }
}

/** 仅给 pointerInput 读坐标用，不触发 Compose 重组（避免每帧 onGloballyPositioned 卡 UI） */
private class LayoutCoordsHolder {
    var coords: LayoutCoordinates? = null
}

/** 行的拖放描述符，用于把「指针落在某行」解析成具体的 TreeDropTarget。 */
private sealed interface RowDropDesc {
    val rowKey: String

    data class CollectionDesc(
        val collectionId: String,
        val collectionIndex: Int,
    ) : RowDropDesc {
        override val rowKey = "coll:$collectionId"
    }

    data class FolderDesc(
        val collectionId: String,
        val folderId: String,
        val parentFolderId: String?,
        val folderIndex: Int,
    ) : RowDropDesc {
        override val rowKey = "folder:$folderId"
    }

    data class RequestDesc(
        val collectionId: String,
        val folderId: String?,
        val requestIndex: Int,
        val requestId: String,
    ) : RowDropDesc {
        override val rowKey = "req:$requestId"
    }
}

private data class ResolvedDrop(
    val rowKey: String,
    val indicator: DropIndicator,
    val target: TreeDropTarget,
)

/** 按「拖拽源类型 × 目标行类型 × 上下半区」解析出最终拖放目标。 */
private fun resolveDrop(
    payload: TreeDragPayload?,
    zones: Map<String, Pair<Rect, RowDropDesc>>,
    point: Offset,
): ResolvedDrop? {
    if (payload == null) return null
    val hit = zones.values
        .filter { it.first.contains(point) }
        .minByOrNull { it.first.height }
        ?: return null
    val (bounds, desc) = hit
    val topHalf = point.y < bounds.top + bounds.height / 2f
    return when (payload) {
        is TreeDragPayload.Collection -> when (desc) {
            is RowDropDesc.CollectionDesc ->
                if (topHalf) {
                    ResolvedDrop(desc.rowKey, DropIndicator.InsertBefore, TreeDropTarget.CollectionSlot(desc.collectionIndex))
                } else {
                    ResolvedDrop(desc.rowKey, DropIndicator.InsertAfter, TreeDropTarget.CollectionSlot(desc.collectionIndex + 1))
                }
            else -> null
        }
        is TreeDragPayload.Folder -> when (desc) {
            is RowDropDesc.FolderDesc ->
                if (topHalf) {
                    ResolvedDrop(desc.rowKey, DropIndicator.InsertBefore, TreeDropTarget.FolderSlot(desc.collectionId, desc.parentFolderId, desc.folderIndex))
                } else {
                    ResolvedDrop(desc.rowKey, DropIndicator.Into, TreeDropTarget.IntoFolder(desc.collectionId, desc.folderId))
                }
            is RowDropDesc.CollectionDesc ->
                ResolvedDrop(desc.rowKey, DropIndicator.Into, TreeDropTarget.IntoCollection(desc.collectionId))
            else -> null
        }
        is TreeDragPayload.Request -> when (desc) {
            is RowDropDesc.RequestDesc ->
                if (topHalf) {
                    ResolvedDrop(desc.rowKey, DropIndicator.InsertBefore, TreeDropTarget.RequestSlot(desc.collectionId, desc.folderId, desc.requestIndex))
                } else {
                    ResolvedDrop(desc.rowKey, DropIndicator.InsertAfter, TreeDropTarget.RequestSlot(desc.collectionId, desc.folderId, desc.requestIndex + 1))
                }
            is RowDropDesc.FolderDesc ->
                ResolvedDrop(desc.rowKey, DropIndicator.Into, TreeDropTarget.IntoFolder(desc.collectionId, desc.folderId))
            is RowDropDesc.CollectionDesc ->
                ResolvedDrop(desc.rowKey, DropIndicator.Into, TreeDropTarget.IntoCollection(desc.collectionId))
        }
    }
}

@Composable
fun CollectionTreeSidebar(
    tree: List<UiCollection>,
    selectedNode: TreeSelection?,
    /** 非 null 时由对应 Request 行滚入树可视区，之后应调用 [onTreeScrollToRequestHandled]。 */
    treeScrollToRequestId: String?,
    onTreeScrollToRequestHandled: () -> Unit,
    editorBoundRequestId: String?,
    expandedCollectionIds: Set<String>,
    expandedFolderIds: Set<String>,
    runningRequestIds: Set<String>,
    mcpCatalogByRequestId: Map<String, McpCatalogSummary> = emptyMap(),
    onToggleCollection: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onMcpToolSelected: (String, McpToolSummary) -> Unit = { _, _ -> },
    onMcpResourceSelected: (String, McpResourceSummary) -> Unit = { _, _ -> },
    onMcpPromptSelected: (String, McpPromptSummary) -> Unit = { _, _ -> },
    onAddCollection: () -> Unit,
    onAddFolder: () -> Unit,
    onAddRequest: () -> Unit,
    /** 右键菜单：在指定集合或文件夹下新建（[TreeSelection] 为 Collection / Folder）。 */
    onContextAddFolder: (TreeSelection) -> Unit,
    onContextAddRequest: (TreeSelection) -> Unit,
    onRename: (TreeSelection, String) -> Unit,
    onDelete: (TreeSelection) -> Unit,
    onCountFolderContents: ((TreeSelection.Folder) -> Pair<Int, Int>)?,
    onSettings: (TreeSelection) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onExportRequestAsGo: (String) -> Unit,
    onExportPostmanCollection: (String) -> Unit,
    onRefreshOpenApiCollection: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    onApplyTreeDrop: (TreeDragPayload, TreeDropTarget) -> Boolean,
    /** 右键菜单：解析剪贴板 cURL 并在指定 Folder / Request 下新建请求。 */
    onImportCurlAt: (TreeSelection) -> Unit = { _ -> },
    folderAddEnabled: Boolean,
    requestAddEnabled: Boolean,
    modifier: Modifier = Modifier,
    /** 外部（如 Ctrl+D 快捷键）请求删除当前选中项；非 null 时弹出删除确认框。 */
    externalDeleteRequest: TreeSelection? = null,
    onExternalDeleteRequestConsumed: () -> Unit = {},
) {
    var renameTarget by remember { mutableStateOf<TreeSelection?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TreeSelection?>(null) }
    var folderDeleteCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var expandedMcpRequestIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(deleteTarget) {
        folderDeleteCounts = if (deleteTarget is TreeSelection.Folder && onCountFolderContents != null) {
            onCountFolderContents(deleteTarget as TreeSelection.Folder)
        } else null
    }

    LaunchedEffect(externalDeleteRequest) {
        if (externalDeleteRequest != null) {
            deleteTarget = externalDeleteRequest
            onExternalDeleteRequestConsumed()
        }
    }

    val dropRegistry = remember { DropZoneRegistry() }
    var treeDragPayload by remember { mutableStateOf<TreeDragPayload?>(null) }
    var treeDragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    val hoveredDrop by remember {
        derivedStateOf {
            resolveDrop(treeDragPayload, dropRegistry.zones, treeDragPointerRoot)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.45f))
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAddRequest,
                enabled = requestAddEnabled,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新建请求",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.onSurface.copy(
                        alpha = if (requestAddEnabled) ContentAlpha.medium else ContentAlpha.disabled
                    )
                )
            }
            IconButton(
                onClick = {
                    val s = selectedNode ?: return@IconButton
                    val name = findTreeLabel(tree, s) ?: return@IconButton
                    renameTarget = s
                    renameText = name
                },
                enabled = selectedNode != null,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "重命名",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.onSurface.copy(
                        alpha = if (selectedNode != null) ContentAlpha.medium else ContentAlpha.disabled
                    )
                )
            }
            IconButton(
                onClick = {
                    val s = selectedNode ?: return@IconButton
                    deleteTarget = s
                },
                enabled = selectedNode != null,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.onSurface.copy(
                        alpha = if (selectedNode != null) ContentAlpha.medium else ContentAlpha.disabled
                    )
                )
            }
        }
        Divider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        )
        val listState = rememberLazyListState()
        val flatItems = remember(tree, expandedCollectionIds, expandedFolderIds) {
            buildFlatTree(tree, expandedCollectionIds, expandedFolderIds)
        }

        LaunchedEffect(treeScrollToRequestId, flatItems) {
            val id = treeScrollToRequestId ?: return@LaunchedEffect
            val index = flatItems.indexOfFirst { it is FlatRequest && it.req.id == id }
            if (index < 0) return@LaunchedEffect
            listState.scrollToItem(index)
            onTreeScrollToRequestHandled()
        }

        val dragActive = treeDragPayload != null
        val onTreeDragStart: (TreeDragPayload, Offset) -> Unit = { payload, rootPos ->
            treeDragPayload = payload
            treeDragPointerRoot = rootPos
        }
        val onTreeDragMove: (Offset) -> Unit = { rootPos -> treeDragPointerRoot = rootPos }
        val onTreeDragEnd: () -> Unit = {
            val p = treeDragPayload
            val hit = resolveDrop(p, dropRegistry.zones, treeDragPointerRoot)
            treeDragPayload = null
            if (p != null && hit != null) {
                onApplyTreeDrop(p, hit.target)
            }
        }
        val onToggleMcpRequest: (String) -> Unit = { id ->
            expandedMcpRequestIds = if (id in expandedMcpRequestIds) expandedMcpRequestIds - id else expandedMcpRequestIds + id
        }
        val onBeginTreeRename: (TreeSelection, String) -> Unit = { sel, name ->
            renameTarget = sel
            renameText = name
        }
        val onDeleteRequest: (TreeSelection) -> Unit = { deleteTarget = it }

        ContextMenuArea(
            items = {
                buildList {
                    add(ContextMenuItem("新建集合") { onAddCollection() })
                }
            }
        ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (tree.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "暂无集合",
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                    TextButton(onClick = onAddCollection) {
                        Text("新建集合", fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    userScrollEnabled = !dragActive,
                ) {
                    items(flatItems, key = { it.key }) { item ->
                        val dropIndicator = if (hoveredDrop?.rowKey == item.key) hoveredDrop!!.indicator else DropIndicator.None
                        when (item) {
                            is FlatCollection -> CollectionTreeBlock(
                                collection = item.collection,
                                depth = 0,
                                selectedNode = selectedNode,
                                showDivider = item.showDivider,
                                expanded = item.collection.id in expandedCollectionIds,
                                collectionIndex = item.collectionIndex,
                                onToggleCollection = onToggleCollection,
                                onSelectNode = onSelectNode,
                                onBeginTreeRename = onBeginTreeRename,
                                onContextAddFolder = onContextAddFolder,
                                onContextAddRequest = onContextAddRequest,
                                onSettings = onSettings,
                                onExportPostmanCollection = onExportPostmanCollection,
                                onRefreshOpenApiCollection = onRefreshOpenApiCollection,
                                dropRegistry = dropRegistry,
                                dropIndicator = dropIndicator,
                                onTreeDragStart = onTreeDragStart,
                                onTreeDragMove = onTreeDragMove,
                                onTreeDragEnd = onTreeDragEnd,
                            )
                            is FlatFolder -> FolderTreeBlock(
                                collectionId = item.collectionId,
                                folder = item.folder,
                                depth = item.depth,
                                selectedNode = selectedNode,
                                expanded = item.folder.id in expandedFolderIds,
                                parentFolderId = item.parentFolderId,
                                folderIndex = item.folderIndex,
                                onToggleFolder = onToggleFolder,
                                onSelectNode = onSelectNode,
                                onBeginTreeRename = onBeginTreeRename,
                                onContextAddFolder = onContextAddFolder,
                                onContextAddRequest = onContextAddRequest,
                                onDeleteRequest = onDeleteRequest,
                                onSettings = onSettings,
                                onImportCurlAt = onImportCurlAt,
                                dropRegistry = dropRegistry,
                                dropIndicator = dropIndicator,
                                onTreeDragStart = onTreeDragStart,
                                onTreeDragMove = onTreeDragMove,
                                onTreeDragEnd = onTreeDragEnd,
                                inheritedColor = item.inheritedColor,
                            )
                            is FlatRequest -> RequestTreeRow(
                                req = item.req,
                                depth = item.depth,
                                selectedNode = selectedNode,
                                editorBoundRequestId = editorBoundRequestId,
                                runningRequestIds = runningRequestIds,
                                mcpCatalog = mcpCatalogByRequestId[item.req.id] ?: McpCatalogSummary(),
                                collectionId = item.collectionId,
                                folderId = item.folderId,
                                requestIndex = item.requestIndex,
                                onSelectNode = onSelectNode,
                                onMcpToolSelected = onMcpToolSelected,
                                onMcpResourceSelected = onMcpResourceSelected,
                                onMcpPromptSelected = onMcpPromptSelected,
                                mcpExpanded = item.req.id in expandedMcpRequestIds,
                                onToggleMcpRequest = onToggleMcpRequest,
                                onBeginTreeRename = onBeginTreeRename,
                                onExportRequestAsCurl = onExportRequestAsCurl,
                                onExportRequestAsGo = onExportRequestAsGo,
                                onDuplicateRequestBelow = onDuplicateRequestBelow,
                                onImportCurlAt = onImportCurlAt,
                                onDeleteRequest = onDeleteRequest,
                                dropRegistry = dropRegistry,
                                dropIndicator = dropIndicator,
                                onTreeDragStart = onTreeDragStart,
                                onTreeDragMove = onTreeDragMove,
                                onTreeDragEnd = onTreeDragEnd,
                                inheritedColor = item.inheritedColor,
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState)
                )
            }
        }
        }
    }

    renameTarget?.let { target ->
        val commitRename = {
            val t = renameText.trim()
            if (t.isNotEmpty()) {
                onRename(target, t)
            }
            renameTarget = null
        }
        val renameFieldFocus = remember { FocusRequester() }
        LaunchedEffect(target) {
            renameFieldFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("名称") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitRename() }),
                    modifier = Modifier
                        .focusRequester(renameFieldFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                                commitRename()
                                true
                            } else {
                                false
                            }
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = commitRename) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        val label = findTreeLabel(tree, target) ?: "该项"
        val message = if (target is TreeSelection.Folder) {
            val (fc, rc) = folderDeleteCounts ?: (0 to 0)
            buildString {
                append("删除「$label」？\n")
                val parts = mutableListOf<String>()
                if (fc > 0) parts.add("$fc 个文件夹")
                if (rc > 0) parts.add("$rc 个请求")
                if (parts.isNotEmpty()) {
                    append("将删除 ${parts.joinToString("、")}。")
                } else {
                    append("该文件夹为空。")
                }
            }
        } else {
            "删除「$label」？子项会一并删除。"
        }
        val confirmFocus = remember { FocusRequester() }
        LaunchedEffect(target) { confirmFocus.requestFocus() }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除", fontSize = 16.sp) },
            text = { Text(message, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    },
                    modifier = Modifier.focusRequester(confirmFocus),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CollectionTreeBlock(
    collection: UiCollection,
    depth: Int,
    selectedNode: TreeSelection?,
    /** 是否在行上方绘制分隔线，用于区分多个 Collection。 */
    showDivider: Boolean = false,
    expanded: Boolean,
    collectionIndex: Int,
    onToggleCollection: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onBeginTreeRename: (TreeSelection, String) -> Unit,
    onContextAddFolder: (TreeSelection) -> Unit,
    onContextAddRequest: (TreeSelection) -> Unit,
    onSettings: (TreeSelection) -> Unit,
    onExportPostmanCollection: (String) -> Unit,
    onRefreshOpenApiCollection: (String) -> Unit,
    dropRegistry: DropZoneRegistry,
    dropIndicator: DropIndicator,
    onTreeDragStart: (TreeDragPayload, Offset) -> Unit,
    onTreeDragMove: (Offset) -> Unit,
    onTreeDragEnd: () -> Unit,
) {
    val isSelected = selectedNode is TreeSelection.Collection && selectedNode.id == collection.id
    val rowLc = remember(collection.id) { LayoutCoordsHolder() }
    val payload = TreeDragPayload.Collection(collection.id)
    val desc = RowDropDesc.CollectionDesc(collection.id, collectionIndex)
    DisposableEffect(desc.rowKey) {
        onDispose { dropRegistry.removeKey(desc.rowKey) }
    }
    if (showDivider) {
        Divider(
            modifier = Modifier.padding(vertical = 3.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        )
    }
    val collSel = TreeSelection.Collection(collection.id)
    val nodeColor = parseHexColorOrNull(collection.color.orEmpty())
    ContextMenuArea(
        items = {
            buildList {
                add(ContextMenuItem("新建文件夹") { onContextAddFolder(collSel) })
                add(ContextMenuItem("新建请求") { onContextAddRequest(collSel) })
                if (!collection.openApiSourceUrl.isNullOrBlank()) {
                    add(ContextMenuItem("刷新 OpenAPI") { onRefreshOpenApiCollection(collection.id) })
                }
                add(ContextMenuItem("导出 Postman v2.1…") { onExportPostmanCollection(collection.id) })
                add(ContextMenuItem("设置") { onSettings(collSel) })
            }
        }
    ) {
        TreeRow(
            depth = depth,
            contentColor = nodeColor,
            icon = {
                Icon(
                    CustomIcons.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = nodeColor ?: MaterialTheme.colors.primary.copy(alpha = 0.9f)
                )
            },
            expandIcon = {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "折叠" else "展开",
                    modifier = Modifier.size(20.dp).clickable { onToggleCollection(collection.id) },
                    tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                )
            },
            label = collection.name,
            selected = isSelected,
            onClick = { onSelectNode(collSel) },
            onDoubleClick = {
                onSelectNode(collSel)
                onBeginTreeRename(collSel, collection.name)
            },
            rowExtraModifier = Modifier.onGloballyPositioned { lc ->
                rowLc.coords = lc
                dropRegistry.sync(desc.rowKey, lc.boundsInRoot(), desc)
            },
            dragModifier = Modifier.pointerInput(payload) {
                detectDragGestures(
                    onDragStart = { offset -> rowLc.coords?.localToRoot(offset)?.let { onTreeDragStart(payload, it) } },
                    onDrag = { change, _ ->
                        change.consume()
                        rowLc.coords?.localToRoot(change.position)?.let(onTreeDragMove)
                    },
                    onDragEnd = { onTreeDragEnd() },
                    onDragCancel = { onTreeDragEnd() },
                )
            },
            dropIndicator = dropIndicator,
            rowHeight = 28.dp,
            labelFontSize = 16.sp,
            edgeBarColor = MaterialTheme.colors.onSurface.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun FolderTreeBlock(
    collectionId: String,
    folder: UiFolder,
    depth: Int,
    selectedNode: TreeSelection?,
    expanded: Boolean,
    parentFolderId: String?,
    folderIndex: Int,
    onToggleFolder: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onBeginTreeRename: (TreeSelection, String) -> Unit,
    onContextAddFolder: (TreeSelection) -> Unit,
    onContextAddRequest: (TreeSelection) -> Unit,
    onDeleteRequest: (TreeSelection) -> Unit,
    onSettings: (TreeSelection) -> Unit,
    onImportCurlAt: (TreeSelection) -> Unit,
    dropRegistry: DropZoneRegistry,
    dropIndicator: DropIndicator,
    onTreeDragStart: (TreeDragPayload, Offset) -> Unit,
    onTreeDragMove: (Offset) -> Unit,
    onTreeDragEnd: () -> Unit,
    /** 从祖先文件夹继承下来的颜色（已解析），用于请求与未设置颜色的子文件夹。 */
    inheritedColor: Color? = null,
) {
    val isSelected = selectedNode is TreeSelection.Folder && selectedNode.id == folder.id
    val rowLc = remember(folder.id) { LayoutCoordsHolder() }
    val payload = TreeDragPayload.Folder(folder.id)
    val desc = RowDropDesc.FolderDesc(collectionId, folder.id, parentFolderId, folderIndex)
    DisposableEffect(desc.rowKey) {
        onDispose { dropRegistry.removeKey(desc.rowKey) }
    }
    val folderSel = TreeSelection.Folder(folder.id)
    val nodeColor = parseHexColorOrNull(folder.color.orEmpty()) ?: inheritedColor
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("新建文件夹") { onContextAddFolder(folderSel) },
                ContextMenuItem("新建请求") { onContextAddRequest(folderSel) },
                ContextMenuItem("解析 cURL") { onImportCurlAt(folderSel) },
                ContextMenuItem("删除") { onDeleteRequest(folderSel) },
                ContextMenuItem("设置") { onSettings(folderSel) },
            )
        }
    ) {
        TreeRow(
            depth = depth,
            contentColor = nodeColor,
            icon = {
                Icon(
                    if (expanded) CustomIcons.FolderOpen else CustomIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = nodeColor ?: MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                )
            },
            expandIcon = {
                val hasChildren = folder.children.isNotEmpty() || folder.requests.isNotEmpty()
                if (hasChildren) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "折叠" else "展开",
                        modifier = Modifier.size(20.dp).clickable { onToggleFolder(folder.id) },
                        tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                    )
                } else {
                    Spacer(Modifier.width(20.dp))
                }
            },
            label = folder.name,
            selected = isSelected,
            onClick = { onSelectNode(folderSel) },
            onDoubleClick = {
                onSelectNode(folderSel)
                onBeginTreeRename(folderSel, folder.name)
            },
            rowExtraModifier = Modifier
                .heightIn(max = 26.dp)
                .padding(vertical = 0.dp)
                .onGloballyPositioned { lc ->
                    rowLc.coords = lc
                    dropRegistry.sync(desc.rowKey, lc.boundsInRoot(), desc)
                },
            dragModifier = Modifier.pointerInput(payload) {
                detectDragGestures(
                    onDragStart = { offset ->
                        rowLc.coords?.localToRoot(offset)?.let { onTreeDragStart(payload, it) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        rowLc.coords?.localToRoot(change.position)?.let(onTreeDragMove)
                    },
                    onDragEnd = { onTreeDragEnd() },
                    onDragCancel = { onTreeDragEnd() },
                )
            },
            dropIndicator = dropIndicator,
        )
    }
}

@Composable
private fun RequestTreeRow(
    req: UiRequestSummary,
    depth: Int,
    selectedNode: TreeSelection?,
    editorBoundRequestId: String?,
    runningRequestIds: Set<String>,
    mcpCatalog: McpCatalogSummary,
    collectionId: String,
    folderId: String?,
    requestIndex: Int,
    onSelectNode: (TreeSelection) -> Unit,
    onMcpToolSelected: (String, McpToolSummary) -> Unit,
    onMcpResourceSelected: (String, McpResourceSummary) -> Unit,
    onMcpPromptSelected: (String, McpPromptSummary) -> Unit,
    mcpExpanded: Boolean,
    onToggleMcpRequest: (String) -> Unit,
    onBeginTreeRename: (TreeSelection, String) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onExportRequestAsGo: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    onImportCurlAt: (TreeSelection) -> Unit,
    onDeleteRequest: (TreeSelection) -> Unit,
    dropRegistry: DropZoneRegistry,
    dropIndicator: DropIndicator,
    onTreeDragStart: (TreeDragPayload, Offset) -> Unit,
    onTreeDragMove: (Offset) -> Unit,
    onTreeDragEnd: () -> Unit,
    /** 从最近父级文件夹继承的颜色；null 表示跟随主题。 */
    inheritedColor: Color? = null,
) {
    val isTreeSelected = selectedNode is TreeSelection.Request && selectedNode.id == req.id
    val editingThis = editorBoundRequestId == req.id
    val isRunning = req.id in runningRequestIds
    val isMcp = req.method.uppercase() == "MCP"
    val hasMcpChildren = isMcp && !mcpCatalog.isEmpty
    val rowLc = remember(req.id) { LayoutCoordsHolder() }
    val payload = TreeDragPayload.Request(req.id)
    val desc = RowDropDesc.RequestDesc(collectionId, folderId, requestIndex, req.id)
    DisposableEffect(desc.rowKey) {
        onDispose { dropRegistry.removeKey(desc.rowKey) }
    }
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("解析 cURL") { onImportCurlAt(TreeSelection.Request(req.id)) },
                ContextMenuItem("cURL") { onExportRequestAsCurl(req.id) },
                ContextMenuItem("Go") { onExportRequestAsGo(req.id) },
                ContextMenuItem("复制") { onDuplicateRequestBelow(req.id) },
                ContextMenuItem("重命名") { onBeginTreeRename(TreeSelection.Request(req.id), req.name) },
                ContextMenuItem("删除") { onDeleteRequest(TreeSelection.Request(req.id)) },
            )
        }
    ) {
        val folderNesting = (depth - 1).coerceAtLeast(0)
        val methodColor = when (req.method.uppercase()) {
            "GET" -> Color(0xFF4CAF50)
            "POST" -> MaterialTheme.colors.primary
            "MCP" -> MaterialTheme.colors.primary
            else -> Color(0xFFE65100)
        }
        val methodBadgeBg = methodColor.copy(alpha = if (folderNesting % 2 == 0) 0.18f else 0.10f)
        val methodBadgeText = methodColor.copy(alpha = 0.88f)
        val methodIconColumnW = 36.dp
        TreeRow(
            depth = depth,
            iconColumnWidth = methodIconColumnW,
            iconNameSpacing = 6.dp,
            contentColor = inheritedColor,
            icon = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = methodIconColumnW)
                                .background(methodBadgeBg, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                req.method.uppercase(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                color = methodBadgeText,
                            )
                        }
                    }
                }
            },
            expandIcon = {
                if (hasMcpChildren) {
                    Icon(
                        if (mcpExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (mcpExpanded) "折叠 MCP" else "展开 MCP",
                        modifier = Modifier.size(20.dp).clickable { onToggleMcpRequest(req.id) },
                        tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                    )
                } else {
                    Spacer(Modifier.width(20.dp))
                }
            },
            label = req.name,
            selected = isTreeSelected || editingThis,
            onClick = { onSelectNode(TreeSelection.Request(req.id)) },
            onDoubleClick = {
                onSelectNode(TreeSelection.Request(req.id))
                onBeginTreeRename(TreeSelection.Request(req.id), req.name)
            },
            rowExtraModifier = Modifier
                .onGloballyPositioned { lc ->
                    rowLc.coords = lc
                    dropRegistry.sync(desc.rowKey, lc.boundsInRoot(), desc)
                },
            dragModifier = Modifier.pointerInput(payload) {
                detectDragGestures(
                    onDragStart = { offset -> rowLc.coords?.localToRoot(offset)?.let { onTreeDragStart(payload, it) } },
                    onDrag = { change, _ ->
                        change.consume()
                        rowLc.coords?.localToRoot(change.position)?.let(onTreeDragMove)
                    },
                    onDragEnd = { onTreeDragEnd() },
                    onDragCancel = { onTreeDragEnd() },
                )
            },
            dropIndicator = dropIndicator,
        )
    }
    if (hasMcpChildren && mcpExpanded) {
        McpCatalogChildren(
            requestId = req.id,
            catalog = mcpCatalog,
            depth = depth + 1,
            onSelectRequest = { onSelectNode(TreeSelection.Request(req.id)) },
            onToolSelected = onMcpToolSelected,
            onResourceSelected = onMcpResourceSelected,
            onPromptSelected = onMcpPromptSelected,
        )
    }
}

@Composable
private fun McpCatalogChildren(
    requestId: String,
    catalog: McpCatalogSummary,
    depth: Int,
    onSelectRequest: () -> Unit,
    onToolSelected: (String, McpToolSummary) -> Unit,
    onResourceSelected: (String, McpResourceSummary) -> Unit,
    onPromptSelected: (String, McpPromptSummary) -> Unit,
) {
    McpCatalogGroup("Tools", catalog.tools, depth, "{}", onSelectRequest) { onToolSelected(requestId, it) }
    McpCatalogGroup("Resources", catalog.resources, depth, "R", onSelectRequest) { onResourceSelected(requestId, it) }
    McpCatalogGroup("Prompts", catalog.prompts, depth, "P", onSelectRequest) { onPromptSelected(requestId, it) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> McpCatalogGroup(
    title: String,
    items: List<T>,
    depth: Int,
    itemIcon: String,
    onSelectRequest: () -> Unit,
    onItemSelected: (T) -> Unit,
) {
    if (items.isEmpty()) return
    TreeRow(
        depth = depth,
        iconColumnWidth = 26.dp,
        iconNameSpacing = 6.dp,
        icon = {
            Text("MCP", fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colors.primary.copy(alpha = 0.9f))
        },
        expandIcon = { Spacer(Modifier.width(20.dp)) },
        label = title,
        selected = false,
        onClick = onSelectRequest,
        rowExtraModifier = Modifier.heightIn(max = 24.dp),
    )
    items.forEach { item ->
        val tooltipText = mcpItemTooltip(item)
        TooltipArea(
            tooltip = { McpItemTooltip(tooltipText) },
            delayMillis = 350,
            tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(12.dp, 12.dp)),
        ) {
            TreeRow(
                depth = depth + 1,
                iconColumnWidth = 18.dp,
                iconNameSpacing = 6.dp,
                icon = {
                    Text(itemIcon, fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium))
                },
                expandIcon = { Spacer(Modifier.width(20.dp)) },
                label = mcpItemLabel(item),
                selected = false,
                onClick = {
                    onSelectRequest()
                    onItemSelected(item)
                },
                rowExtraModifier = Modifier.heightIn(max = 24.dp),
            )
        }
    }
}

@Composable
private fun McpItemTooltip(text: String) {
    Surface(
        color = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface,
        elevation = 6.dp,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text.ifBlank { "No description" },
            modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

private fun mcpItemLabel(item: Any?): String = when (item) {
    is McpToolSummary -> item.name
    is McpResourceSummary -> item.name
    is McpPromptSummary -> item.name
    else -> item?.toString().orEmpty()
}

private fun mcpItemTooltip(item: Any?): String = when (item) {
    is McpToolSummary -> buildString {
        if (item.description.isNotBlank()) append(item.description)
        val properties = item.inputSchema?.get("properties") as? JsonObject
        if (properties != null && properties.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("Params: ")
            append(properties.keys.joinToString(", "))
        }
    }
    is McpResourceSummary -> buildString {
        if (item.description.isNotBlank()) append(item.description)
        if (item.uri.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("URI: ").append(item.uri)
        }
        if (item.mimeType.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("MIME: ").append(item.mimeType)
        }
    }
    is McpPromptSummary -> buildString {
        if (item.description.isNotBlank()) append(item.description)
        val names = item.arguments?.mapNotNull { arg ->
            (arg as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }.orEmpty()
        if (names.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("Args: ").append(names.joinToString(", "))
        }
    }
    else -> ""
}

@Composable
private fun TreeRow(
    depth: Int,
    icon: @Composable () -> Unit,
    expandIcon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    /** 第二次点击落在系统双击间隔内触发，不阻塞第一次单击（避免 combinedClickable 的延迟） */
    onDoubleClick: (() -> Unit)? = null,
    rowExtraModifier: Modifier = Modifier,
    /**
     * 在内侧、[clickable] 外侧：先收到指针，便于拖动手势。
     */
    dragModifier: Modifier = Modifier,
    dropIndicator: DropIndicator = DropIndicator.None,
    /** 方法列等图标的固定宽度，Request 可略小以视觉更轻。 */
    iconColumnWidth: Dp = 42.dp,
    /** 图标与标题名之间的留白。 */
    iconNameSpacing: Dp = 0.dp,
    /** 名称文字颜色；null 时回退到主题 onSurface。 */
    contentColor: Color? = null,
    /** 行高，Collection 等层级行可调大以更显眼。 */
    rowHeight: Dp = 24.dp,
    /** 名称字号，lineHeight 随之缩放。 */
    labelFontSize: TextUnit = 15.sp,
    /** 行最左的窄竖条颜色；null 时不绘制，用于区分层级行。 */
    edgeBarColor: Color? = null,
) {
    val doubleTapMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var lastClickMs by remember { mutableStateOf(0L) }
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(2.dp))
        .background(
            when {
                dropIndicator == DropIndicator.Into -> MaterialTheme.colors.primary.copy(alpha = 0.14f)
                selected -> MaterialTheme.colors.primary.copy(alpha = 0.16f)
                else -> Color.Transparent
            }
        )
    val clickableModifier = if (onDoubleClick != null) {
        val onDbl = onDoubleClick
        rowModifier.clickable(onClick = {
            val now = System.currentTimeMillis()
            if (lastClickMs != 0L && now - lastClickMs < doubleTapMs) {
                onDbl()
                lastClickMs = 0L
            } else {
                onClick()
                lastClickMs = now
            }
        })
    } else {
        rowModifier.clickable(onClick = onClick)
    }
    Box(
        modifier = rowExtraModifier
            .then(clickableModifier)
            .then(dragModifier)
            .height(rowHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = (depth * 6).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(23.dp), contentAlignment = Alignment.Center) {
                expandIcon()
            }
            Box(Modifier.width(iconColumnWidth), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(Modifier.width(iconNameSpacing))
            Text(
                label,
                style = TextStyle(
                    fontSize = labelFontSize,
                    lineHeight = labelFontSize * 1.2f,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor ?: MaterialTheme.colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        val barColor = edgeBarColor
        if (barColor != null) {
            Canvas(Modifier.matchParentSize()) {
                val barWidth = 3.dp.toPx()
                drawRect(barColor, topLeft = Offset.Zero, size = Size(barWidth, size.height))
            }
        }
        if (dropIndicator == DropIndicator.InsertBefore || dropIndicator == DropIndicator.InsertAfter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(if (dropIndicator == DropIndicator.InsertBefore) Alignment.TopCenter else Alignment.BottomCenter)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.75f))
            )
        }
    }
}

fun findTreeLabel(tree: List<UiCollection>, sel: TreeSelection): String? {
    return when (sel) {
        is TreeSelection.Collection -> tree.find { it.id == sel.id }?.name
        is TreeSelection.Folder -> findFolderName(tree, sel.id)
        is TreeSelection.Request -> findRequestName(tree, sel.id)
    }
}

private fun findFolderName(collections: List<UiCollection>, folderId: String): String? {
    for (c in collections) {
        findFolderNameInFolders(c.folders, folderId)?.let { return it }
    }
    return null
}

private fun findFolderNameInFolders(folders: List<UiFolder>, folderId: String): String? {
    for (f in folders) {
        if (f.id == folderId) return f.name
        findFolderNameInFolders(f.children, folderId)?.let { return it }
    }
    return null
}

private fun findRequestName(collections: List<UiCollection>, requestId: String): String? {
    for (c in collections) {
        c.rootRequests.find { it.id == requestId }?.let { return it.name }
        findRequestNameInFolders(c.folders, requestId)?.let { return it }
    }
    return null
}

private fun findRequestNameInFolders(folders: List<UiFolder>, requestId: String): String? {
    for (f in folders) {
        f.requests.find { it.id == requestId }?.let { return it.name }
        findRequestNameInFolders(f.children, requestId)?.let { return it }
    }
    return null
}

fun firstRequestSelection(tree: List<UiCollection>): TreeSelection.Request? {
    for (c in tree) {
        c.rootRequests.firstOrNull()?.let { return TreeSelection.Request(it.id) }
        firstRequestInFolders(c.folders)?.let { return it }
    }
    return null
}

/** 为在侧栏中露出某请求，需要额外展开的集合 id 与文件夹 id（含从根到父文件夹的链）。 */
fun expandSetsForRequest(tree: List<UiCollection>, requestId: String): Pair<Set<String>, Set<String>>? {
    for (c in tree) {
        if (c.rootRequests.any { it.id == requestId }) {
            return setOf(c.id) to emptySet()
        }
        folderPathContainingRequest(c.folders, requestId)?.let { path ->
            return setOf(c.id) to path.toSet()
        }
    }
    return null
}

private fun folderPathContainingRequest(folders: List<UiFolder>, requestId: String): List<String>? {
    for (f in folders) {
        if (f.requests.any { it.id == requestId }) {
            return listOf(f.id)
        }
        folderPathContainingRequest(f.children, requestId)?.let { tail ->
            return listOf(f.id) + tail
        }
    }
    return null
}

private fun firstRequestInFolders(folders: List<UiFolder>): TreeSelection.Request? {
    for (f in folders) {
        f.requests.firstOrNull()?.let { return TreeSelection.Request(it.id) }
        firstRequestInFolders(f.children)?.let { return it }
    }
    return null
}

fun collectAllFolderIds(tree: List<UiCollection>): Set<String> {
    val out = mutableSetOf<String>()
    for (c in tree) {
        collectFolderIds(c.folders, out)
    }
    return out
}

private fun collectFolderIds(folders: List<UiFolder>, out: MutableSet<String>) {
    for (f in folders) {
        out += f.id
        collectFolderIds(f.children, out)
    }
}

// ── LazyColumn 扁平化 ─────────────────────────────────────
// 把递归树展平为一维可见行列表，供 LazyColumn 按需渲染。

private sealed interface FlatItem {
    val key: String
}

private data class FlatCollection(
    val collection: UiCollection,
    val showDivider: Boolean,
    val collectionIndex: Int,
) : FlatItem {
    override val key = "coll:${collection.id}"
}

private data class FlatFolder(
    val collectionId: String,
    val folder: UiFolder,
    val depth: Int,
    val inheritedColor: Color?,
    val parentFolderId: String?,
    val folderIndex: Int,
) : FlatItem {
    override val key = "folder:${folder.id}"
}

private data class FlatRequest(
    val req: UiRequestSummary,
    val depth: Int,
    val inheritedColor: Color?,
    val collectionId: String,
    val folderId: String?,
    val requestIndex: Int,
) : FlatItem {
    override val key = "req:${req.id}"
}

private fun buildFlatTree(
    tree: List<UiCollection>,
    expandedCollectionIds: Set<String>,
    expandedFolderIds: Set<String>,
): List<FlatItem> {
    val out = mutableListOf<FlatItem>()
    tree.forEachIndexed { index, collection ->
        out += FlatCollection(collection, showDivider = index != 0, collectionIndex = index)
        if (collection.id in expandedCollectionIds) {
            val cid = collection.id
            collection.folders.forEachIndexed { i, folder ->
                out += FlatFolder(cid, folder, depth = 1, inheritedColor = null, parentFolderId = null, folderIndex = i)
                if (folder.id in expandedFolderIds) {
                    emitFolderContents(out, cid, folder, depth = 1, inheritedColor = null, expandedFolderIds)
                }
            }
            collection.rootRequests.forEachIndexed { i, req ->
                out += FlatRequest(req, depth = 1, inheritedColor = null, collectionId = cid, folderId = null, requestIndex = i)
            }
        }
    }
    return out
}

private fun emitFolderContents(
    out: MutableList<FlatItem>,
    collectionId: String,
    folder: UiFolder,
    depth: Int,
    inheritedColor: Color?,
    expandedFolderIds: Set<String>,
) {
    val nodeColor = parseHexColorOrNull(folder.color.orEmpty()) ?: inheritedColor
    val fid = folder.id
    folder.children.forEachIndexed { i, child ->
        out += FlatFolder(collectionId, child, depth + 1, inheritedColor = nodeColor, parentFolderId = fid, folderIndex = i)
        if (child.id in expandedFolderIds) {
            emitFolderContents(out, collectionId, child, depth + 1, nodeColor, expandedFolderIds)
        }
    }
    folder.requests.forEachIndexed { i, req ->
        out += FlatRequest(req, depth + 1, inheritedColor = nodeColor, collectionId = collectionId, folderId = fid, requestIndex = i)
    }
}
