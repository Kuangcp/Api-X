package tree

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.LayoutCoordinates

private class DropZoneRegistry {
    val zones = mutableStateMapOf<String, Pair<Rect, TreeDropTarget>>()
    fun sync(key: String, active: Boolean, bounds: Rect, target: TreeDropTarget) {
        if (active) zones[key] = bounds to target
        else zones.remove(key)
    }
    fun removeKey(key: String) {
        zones.remove(key)
    }
}

/** 仅给 pointerInput 读坐标用，不触发 Compose 重组（避免每帧 onGloballyPositioned 卡 UI） */
private class LayoutCoordsHolder {
    var coords: LayoutCoordinates? = null
}

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
    modifier: Modifier = Modifier,
) {
    val highlight = active && hovered == target
    LaunchedEffect(active, zoneKey) {
        if (!active) dropRegistry.removeKey(zoneKey)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(if (active) 4.dp else 0.dp)
            .then(
                if (active) {
                    Modifier.onGloballyPositioned { lc ->
                        dropRegistry.sync(zoneKey, true, lc.boundsInRoot(), target)
                    }
                } else {
                    Modifier
                }
            )
            .background(
                when {
                    !active -> Color.Transparent
                    highlight -> MaterialTheme.colors.primary.copy(alpha = 0.38f)
                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.07f)
                }
            )
    )
}

@Composable
fun CollectionTreeSidebar(
    tree: List<UiCollection>,
    selectedNode: TreeSelection?,
    editorBoundRequestId: String?,
    expandedCollectionIds: Set<String>,
    expandedFolderIds: Set<String>,
    onToggleCollection: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onAddCollection: () -> Unit,
    onAddFolder: () -> Unit,
    onAddRequest: () -> Unit,
    /** 右键菜单：在指定集合或文件夹下新建（[TreeSelection] 为 Collection / Folder）。 */
    onContextAddFolder: (TreeSelection) -> Unit,
    onContextAddRequest: (TreeSelection) -> Unit,
    onRename: (TreeSelection, String) -> Unit,
    onDelete: (TreeSelection) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    onApplyTreeDrop: (TreeDragPayload, TreeDropTarget) -> Boolean,
    folderAddEnabled: Boolean,
    requestAddEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<TreeSelection?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TreeSelection?>(null) }

    val dropRegistry = remember { DropZoneRegistry() }
    var treeDragPayload by remember { mutableStateOf<TreeDragPayload?>(null) }
    var treeDragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    val hoveredDropTarget by remember {
        derivedStateOf {
            if (treeDragPayload == null) null
            else bestDropTarget(dropRegistry.zones, treeDragPointerRoot)
        }
    }

    LaunchedEffect(treeDragPayload) {
        if (treeDragPayload == null) dropRegistry.zones.clear()
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
                onClick = onAddCollection,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新建集合",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                )
            }
            IconButton(
                onClick = onAddFolder,
                enabled = folderAddEnabled,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = "新建文件夹",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colors.onSurface.copy(
                        alpha = if (folderAddEnabled) ContentAlpha.medium else ContentAlpha.disabled
                    )
                )
            }
            IconButton(
                onClick = onAddRequest,
                enabled = requestAddEnabled,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.PostAdd,
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
        val scroll = rememberScrollState()
        val scrollWhileNotDragging = treeDragPayload == null
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll, enabled = scrollWhileNotDragging)
            ) {
                if (tree.isEmpty()) {
                    Text(
                        "暂无集合",
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                        modifier = Modifier.padding(6.dp)
                    )
                } else {
                    tree.forEach { coll ->
                        CollectionTreeBlock(
                            collection = coll,
                            depth = 0,
                            selectedNode = selectedNode,
                            editorBoundRequestId = editorBoundRequestId,
                            expandedCollectionIds = expandedCollectionIds,
                            expandedFolderIds = expandedFolderIds,
                            onToggleCollection = onToggleCollection,
                            onToggleFolder = onToggleFolder,
                            onSelectNode = onSelectNode,
                            onBeginTreeRename = { sel, name ->
                                renameTarget = sel
                                renameText = name
                            },
                            onContextAddFolder = onContextAddFolder,
                            onContextAddRequest = onContextAddRequest,
                            onExportRequestAsCurl = onExportRequestAsCurl,
                            onDuplicateRequestBelow = onDuplicateRequestBelow,
                            dragging = treeDragPayload,
                            dropRegistry = dropRegistry,
                            hoveredDropTarget = hoveredDropTarget,
                            onTreeDragStart = { payload, rootPos ->
                                treeDragPayload = payload
                                treeDragPointerRoot = rootPos
                            },
                            onTreeDragMove = { rootPos -> treeDragPointerRoot = rootPos },
                            onTreeDragEnd = {
                                val p = treeDragPayload
                                val hit = bestDropTarget(dropRegistry.zones, treeDragPointerRoot)
                                treeDragPayload = null
                                dropRegistry.zones.clear()
                                if (p != null && hit != null) {
                                    onApplyTreeDrop(p, hit)
                                }
                            },
                        )
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scroll)
            )
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除", fontSize = 16.sp) },
            text = { Text("删除「$label」？子项会一并删除。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    }
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
    editorBoundRequestId: String?,
    expandedCollectionIds: Set<String>,
    expandedFolderIds: Set<String>,
    onToggleCollection: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onBeginTreeRename: (TreeSelection, String) -> Unit,
    onContextAddFolder: (TreeSelection) -> Unit,
    onContextAddRequest: (TreeSelection) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    dragging: TreeDragPayload?,
    dropRegistry: DropZoneRegistry,
    hoveredDropTarget: TreeDropTarget?,
    onTreeDragStart: (TreeDragPayload, Offset) -> Unit,
    onTreeDragMove: (Offset) -> Unit,
    onTreeDragEnd: () -> Unit,
) {
    val expanded = expandedCollectionIds.contains(collection.id)
    val isSelected = selectedNode is TreeSelection.Collection && selectedNode.id == collection.id
    val dragActive = dragging != null
    val intoCollTarget = TreeDropTarget.IntoCollection(collection.id)
    val intoCollHighlight = dragActive && hoveredDropTarget == intoCollTarget
    val intoCollKey = "into-coll-${collection.id}"
    LaunchedEffect(dragActive, collection.id) {
        if (!dragActive) dropRegistry.removeKey(intoCollKey)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (dragActive) {
                    Modifier.onGloballyPositioned { lc ->
                        dropRegistry.sync(intoCollKey, true, lc.boundsInRoot(), intoCollTarget)
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val collSel = TreeSelection.Collection(collection.id)
        ContextMenuArea(
            items = {
                listOf(
                    ContextMenuItem("新建文件夹") { onContextAddFolder(collSel) },
                    ContextMenuItem("新建请求") { onContextAddRequest(collSel) },
                )
            }
        ) {
            TreeRow(
                depth = depth,
                icon = {
                    Icon(
                        Icons.Filled.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colors.primary.copy(alpha = 0.9f)
                    )
                },
                expandIcon = {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
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
                dropTargetHighlight = intoCollHighlight,
            )
        }
    }
    if (expanded) {
        val cid = collection.id
        TreeDropGap(
            active = dragActive,
            zoneKey = "fs-$cid-root-0",
            target = TreeDropTarget.FolderSlot(cid, null, 0),
            dropRegistry = dropRegistry,
            hovered = hoveredDropTarget,
        )
        collection.folders.forEachIndexed { i, folder ->
            FolderTreeBlock(
                collectionId = cid,
                folder = folder,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                expandedFolderIds = expandedFolderIds,
                onToggleFolder = onToggleFolder,
                onSelectNode = onSelectNode,
                onBeginTreeRename = onBeginTreeRename,
                onContextAddFolder = onContextAddFolder,
                onContextAddRequest = onContextAddRequest,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
                dragging = dragging,
                dropRegistry = dropRegistry,
                hoveredDropTarget = hoveredDropTarget,
                onTreeDragStart = onTreeDragStart,
                onTreeDragMove = onTreeDragMove,
                onTreeDragEnd = onTreeDragEnd,
            )
            TreeDropGap(
                active = dragActive,
                zoneKey = "fs-$cid-root-${i + 1}",
                target = TreeDropTarget.FolderSlot(cid, null, i + 1),
                dropRegistry = dropRegistry,
                hovered = hoveredDropTarget,
            )
        }
        TreeDropGap(
            active = dragActive,
            zoneKey = "rs-$cid-root-0",
            target = TreeDropTarget.RequestSlot(cid, null, 0),
            dropRegistry = dropRegistry,
            hovered = hoveredDropTarget,
        )
        collection.rootRequests.forEachIndexed { i, req ->
            RequestTreeRow(
                req = req,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                onSelectNode = onSelectNode,
                onBeginTreeRename = onBeginTreeRename,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
                onTreeDragStart = onTreeDragStart,
                onTreeDragMove = onTreeDragMove,
                onTreeDragEnd = onTreeDragEnd,
            )
            TreeDropGap(
                active = dragActive,
                zoneKey = "rs-$cid-root-${i + 1}",
                target = TreeDropTarget.RequestSlot(cid, null, i + 1),
                dropRegistry = dropRegistry,
                hovered = hoveredDropTarget,
            )
        }
    }
}

@Composable
private fun FolderTreeBlock(
    collectionId: String,
    folder: UiFolder,
    depth: Int,
    selectedNode: TreeSelection?,
    editorBoundRequestId: String?,
    expandedFolderIds: Set<String>,
    onToggleFolder: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onBeginTreeRename: (TreeSelection, String) -> Unit,
    onContextAddFolder: (TreeSelection) -> Unit,
    onContextAddRequest: (TreeSelection) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    dragging: TreeDragPayload?,
    dropRegistry: DropZoneRegistry,
    hoveredDropTarget: TreeDropTarget?,
    onTreeDragStart: (TreeDragPayload, Offset) -> Unit,
    onTreeDragMove: (Offset) -> Unit,
    onTreeDragEnd: () -> Unit,
) {
    val expanded = expandedFolderIds.contains(folder.id)
    val isSelected = selectedNode is TreeSelection.Folder && selectedNode.id == folder.id
    val dragActive = dragging != null
    val intoTarget = TreeDropTarget.IntoFolder(collectionId, folder.id)
    val intoHighlight = dragActive && hoveredDropTarget == intoTarget
    val intoKey = "into-${folder.id}"
    val rowLc = remember(folder.id) { LayoutCoordsHolder() }
    LaunchedEffect(dragActive, folder.id) {
        if (!dragActive) dropRegistry.removeKey(intoKey)
    }
    val payload = TreeDragPayload.Folder(folder.id)
    val folderSel = TreeSelection.Folder(folder.id)
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("新建文件夹") { onContextAddFolder(folderSel) },
                ContextMenuItem("新建请求") { onContextAddRequest(folderSel) },
            )
        }
    ) {
        TreeRow(
            depth = depth,
            icon = {
                Icon(
                    if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                )
            },
            expandIcon = {
                val hasChildren = folder.children.isNotEmpty() || folder.requests.isNotEmpty()
                if (hasChildren) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
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
            rowExtraModifier = Modifier.onGloballyPositioned { lc ->
                rowLc.coords = lc
                if (dragActive) {
                    dropRegistry.sync(intoKey, true, lc.boundsInRoot(), intoTarget)
                }
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
            dropTargetHighlight = intoHighlight,
        )
    }
    if (expanded) {
        val fid = folder.id
        TreeDropGap(
            active = dragActive,
            zoneKey = "fs-$collectionId-$fid-0",
            target = TreeDropTarget.FolderSlot(collectionId, fid, 0),
            dropRegistry = dropRegistry,
            hovered = hoveredDropTarget,
        )
        folder.children.forEachIndexed { i, child ->
            FolderTreeBlock(
                collectionId = collectionId,
                folder = child,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                expandedFolderIds = expandedFolderIds,
                onToggleFolder = onToggleFolder,
                onSelectNode = onSelectNode,
                onBeginTreeRename = onBeginTreeRename,
                onContextAddFolder = onContextAddFolder,
                onContextAddRequest = onContextAddRequest,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
                dragging = dragging,
                dropRegistry = dropRegistry,
                hoveredDropTarget = hoveredDropTarget,
                onTreeDragStart = onTreeDragStart,
                onTreeDragMove = onTreeDragMove,
                onTreeDragEnd = onTreeDragEnd,
            )
            TreeDropGap(
                active = dragActive,
                zoneKey = "fs-$collectionId-$fid-${i + 1}",
                target = TreeDropTarget.FolderSlot(collectionId, fid, i + 1),
                dropRegistry = dropRegistry,
                hovered = hoveredDropTarget,
            )
        }
        TreeDropGap(
            active = dragActive,
            zoneKey = "rs-$collectionId-$fid-0",
            target = TreeDropTarget.RequestSlot(collectionId, fid, 0),
            dropRegistry = dropRegistry,
            hovered = hoveredDropTarget,
        )
        folder.requests.forEachIndexed { i, req ->
            RequestTreeRow(
                req = req,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                onSelectNode = onSelectNode,
                onBeginTreeRename = onBeginTreeRename,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
                onTreeDragStart = onTreeDragStart,
                onTreeDragMove = onTreeDragMove,
                onTreeDragEnd = onTreeDragEnd,
            )
            TreeDropGap(
                active = dragActive,
                zoneKey = "rs-$collectionId-$fid-${i + 1}",
                target = TreeDropTarget.RequestSlot(collectionId, fid, i + 1),
                dropRegistry = dropRegistry,
                hovered = hoveredDropTarget,
            )
        }
    }
}

@Composable
private fun RequestTreeRow(
    req: UiRequestSummary,
    depth: Int,
    selectedNode: TreeSelection?,
    editorBoundRequestId: String?,
    onSelectNode: (TreeSelection) -> Unit,
    onBeginTreeRename: (TreeSelection, String) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    onTreeDragStart: (TreeDragPayload, Offset) -> Unit,
    onTreeDragMove: (Offset) -> Unit,
    onTreeDragEnd: () -> Unit,
) {
    val isTreeSelected = selectedNode is TreeSelection.Request && selectedNode.id == req.id
    val editingThis = editorBoundRequestId == req.id
    val rowLc = remember(req.id) { LayoutCoordsHolder() }
    val payload = TreeDragPayload.Request(req.id)
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("cURL") {
                    onExportRequestAsCurl(req.id)
                },
                ContextMenuItem("复制") {
                    onDuplicateRequestBelow(req.id)
                },
                ContextMenuItem("重命名") {
                    onBeginTreeRename(TreeSelection.Request(req.id), req.name)
                },
            )
        }
    ) {
        TreeRow(
            depth = depth,
            icon = {
                Text(
                    req.method.uppercase(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            expandIcon = { Spacer(Modifier.width(20.dp)) },
            label = req.name,
            selected = isTreeSelected || (editingThis && !isTreeSelected),
            onClick = { onSelectNode(TreeSelection.Request(req.id)) },
            onDoubleClick = {
                onSelectNode(TreeSelection.Request(req.id))
                onBeginTreeRename(TreeSelection.Request(req.id), req.name)
            },
            rowExtraModifier = Modifier.onGloballyPositioned { rowLc.coords = it },
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
        )
    }
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
    dropTargetHighlight: Boolean = false,
) {
    val doubleTapMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var lastClickMs by remember { mutableStateOf(0L) }
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(2.dp))
        .background(
            when {
                dropTargetHighlight -> MaterialTheme.colors.primary.copy(alpha = 0.14f)
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
    Row(
        modifier = rowExtraModifier
            .then(clickableModifier)
            .then(dragModifier)
            .padding(vertical = 1.dp, horizontal = 0.dp)
            .padding(start = (depth * 6).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(23.dp), contentAlignment = Alignment.Center) {
            expandIcon()
        }
        Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            label,
            style = TextStyle(
                fontSize = 15.sp,
                lineHeight = 16.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f)
        )
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
