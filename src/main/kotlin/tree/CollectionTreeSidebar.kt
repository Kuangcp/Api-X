package tree

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onRename: (TreeSelection, String) -> Unit,
    onDelete: (TreeSelection) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
    folderAddEnabled: Boolean,
    requestAddEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<TreeSelection?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TreeSelection?>(null) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
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
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        )
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
        ) {
            if (tree.isEmpty()) {
                Text(
                    "暂无集合",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    modifier = Modifier.padding(8.dp)
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
                        onRequestRename = { id, name ->
                            renameTarget = TreeSelection.Request(id)
                            renameText = name
                        },
                        onExportRequestAsCurl = onExportRequestAsCurl,
                        onDuplicateRequestBelow = onDuplicateRequestBelow,
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
                    modifier = Modifier.onPreviewKeyEvent { ev ->
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
    onRequestRename: (String, String) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
) {
    val expanded = expandedCollectionIds.contains(collection.id)
    val isSelected = selectedNode is TreeSelection.Collection && selectedNode.id == collection.id
    TreeRow(
        depth = depth,
        icon = {
            Icon(
                Icons.Filled.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.primary.copy(alpha = 0.9f)
            )
        },
        expandIcon = {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "折叠" else "展开",
                modifier = Modifier.size(18.dp).clickable { onToggleCollection(collection.id) },
                tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            )
        },
        label = collection.name,
        selected = isSelected,
        onClick = { onSelectNode(TreeSelection.Collection(collection.id)) },
    )
    if (expanded) {
        collection.folders.forEach { folder ->
            FolderTreeBlock(
                folder = folder,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                expandedFolderIds = expandedFolderIds,
                onToggleFolder = onToggleFolder,
                onSelectNode = onSelectNode,
                onRequestRename = onRequestRename,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
            )
        }
        collection.rootRequests.forEach { req ->
            RequestTreeRow(
                req = req,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                onSelectNode = onSelectNode,
                onRequestRename = onRequestRename,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
            )
        }
    }
}

@Composable
private fun FolderTreeBlock(
    folder: UiFolder,
    depth: Int,
    selectedNode: TreeSelection?,
    editorBoundRequestId: String?,
    expandedFolderIds: Set<String>,
    onToggleFolder: (String) -> Unit,
    onSelectNode: (TreeSelection) -> Unit,
    onRequestRename: (String, String) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
) {
    val expanded = expandedFolderIds.contains(folder.id)
    val isSelected = selectedNode is TreeSelection.Folder && selectedNode.id == folder.id
    TreeRow(
        depth = depth,
        icon = {
            Icon(
                if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
            )
        },
        expandIcon = {
            val hasChildren = folder.children.isNotEmpty() || folder.requests.isNotEmpty()
            if (hasChildren) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).clickable { onToggleFolder(folder.id) },
                    tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                )
            } else {
                Spacer(Modifier.width(18.dp))
            }
        },
        label = folder.name,
        selected = isSelected,
        onClick = { onSelectNode(TreeSelection.Folder(folder.id)) },
    )
    if (expanded) {
        folder.children.forEach { child ->
            FolderTreeBlock(
                folder = child,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                expandedFolderIds = expandedFolderIds,
                onToggleFolder = onToggleFolder,
                onSelectNode = onSelectNode,
                onRequestRename = onRequestRename,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
            )
        }
        folder.requests.forEach { req ->
            RequestTreeRow(
                req = req,
                depth = depth + 1,
                selectedNode = selectedNode,
                editorBoundRequestId = editorBoundRequestId,
                onSelectNode = onSelectNode,
                onRequestRename = onRequestRename,
                onExportRequestAsCurl = onExportRequestAsCurl,
                onDuplicateRequestBelow = onDuplicateRequestBelow,
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
    onRequestRename: (String, String) -> Unit,
    onExportRequestAsCurl: (String) -> Unit,
    onDuplicateRequestBelow: (String) -> Unit,
) {
    val isTreeSelected = selectedNode is TreeSelection.Request && selectedNode.id == req.id
    val editingThis = editorBoundRequestId == req.id
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("导出为 cURL") {
                    onExportRequestAsCurl(req.id)
                },
                ContextMenuItem("复制") {
                    onDuplicateRequestBelow(req.id)
                }
            )
        }
    ) {
        TreeRow(
            depth = depth,
            icon = {
                Text(
                    req.method.uppercase(),
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            expandIcon = { Spacer(Modifier.width(18.dp)) },
            label = req.name,
            selected = isTreeSelected || (editingThis && !isTreeSelected),
            onClick = { onSelectNode(TreeSelection.Request(req.id)) },
            onDoubleClick = {
                onSelectNode(TreeSelection.Request(req.id))
                onRequestRename(req.id, req.name)
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
    onDoubleClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .background(
            if (selected) MaterialTheme.colors.primary.copy(alpha = 0.16f)
            else Color.Transparent
        )
    val clickableModifier = if (onDoubleClick != null) {
        rowModifier.combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
    } else {
        rowModifier.clickable(onClick = onClick)
    }
    Row(
        modifier = clickableModifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .padding(start = (depth * 10).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            expandIcon()
        }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            label,
            fontSize = 12.sp,
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
