package app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import db.CollectionRepository
import tree.TreeSelection
import tree.UiCollection

class TreeState(
    val repository: CollectionRepository,
    expandLoaded: TreeExpandPrefs.Loaded,
) {
    var tree by mutableStateOf(repository.loadTree())
    var treeSelection by mutableStateOf<TreeSelection?>(null)
    var expandedCollectionIds by mutableStateOf(expandLoaded.collectionIds)
    var expandedFolderIds by mutableStateOf(expandLoaded.folderIds)
    var treeSplitRatio by mutableStateOf(0.2f)
    var treeSidebarVisible by mutableStateOf(true)
    var treeScrollToRequestId by mutableStateOf<String?>(null)

    fun refresh() {
        tree = repository.loadTree()
    }

    fun toggleCollection(id: String) {
        expandedCollectionIds = if (id in expandedCollectionIds) expandedCollectionIds - id else expandedCollectionIds + id
    }

    fun toggleFolder(id: String) {
        expandedFolderIds = if (id in expandedFolderIds) expandedFolderIds - id else expandedFolderIds + id
    }

    fun addFolderAt(at: TreeSelection) {
        val target = repository.newFolderTarget(at) ?: return
        val (cid, pid) = target
        repository.createFolder(cid, pid, "新文件夹").let { fid ->
            refresh()
            expandedCollectionIds = expandedCollectionIds + cid
            if (pid != null) expandedFolderIds = expandedFolderIds + pid
            treeSelection = TreeSelection.Folder(fid)
        }
    }
}
