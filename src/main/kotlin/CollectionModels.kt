/** 侧边栏树节点（集合 → 文件夹* → 请求*） */
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
    val bodyText: String,
    val metaJson: String,
)

sealed class TreeSelection {
    data class Collection(val id: String) : TreeSelection()
    data class Folder(val id: String) : TreeSelection()
    data class Request(val id: String) : TreeSelection()
}
