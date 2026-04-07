package tree

/** 侧边栏树 node（集合 → 文件夹* → 请求*） */
data class UiCollection(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val rootRequests: List<UiRequestSummary>,
)

data class UiFolder(
    val id: String,
    val name: String,
    val children: List<UiFolder>,
    val requests: List<UiRequestSummary>,
)

data class UiRequestSummary(
    val id: String,
    val name: String,
    val method: String,
)

/** 完整请求，用于编辑器加载/保存 */
data class StoredHttpRequest(
    val id: String,
    val collectionId: String,
    val folderId: String?,
    val name: String,
    val method: String,
    val url: String,
    val headersText: String,
    val paramsText: String,
    val bodyText: String,
    val metaJson: String,
    val auth: PostmanAuth? = null,
)

sealed class TreeSelection {
    data class Collection(val id: String) : TreeSelection()
    data class Folder(val id: String) : TreeSelection()
    data class Request(val id: String) : TreeSelection()
}

/** 侧边栏拖拽源（仅 Folder / Request 可拖动） */
sealed class TreeDragPayload {
    data class Folder(val id: String) : TreeDragPayload()
    data class Request(val id: String) : TreeDragPayload()
}

/**
 * 拖放目标：同级文件夹槽位、同级请求槽位、放入某文件夹内（末尾）。
 * [insertIndex] 为当前父级下子项列表中的插入下标（0 表示最前）。
 */
sealed class TreeDropTarget {
    data class FolderSlot(
        val collectionId: String,
        val parentFolderId: String?,
        val insertIndex: Int,
    ) : TreeDropTarget()

    data class RequestSlot(
        val collectionId: String,
        val folderId: String?,
        val insertIndex: Int,
    ) : TreeDropTarget()

    data class IntoFolder(
        val collectionId: String,
        val folderId: String,
    ) : TreeDropTarget()

    /** 放入集合根（仅支持同一集合内：根下文件夹末尾或根下请求末尾） */
    data class IntoCollection(
        val collectionId: String,
    ) : TreeDropTarget()
}
