package tree

/** 集合与文件夹路径标签（不含请求名），用于全局搜索等场景。 */
fun requestParentLocationLabel(
    tree: List<UiCollection>,
    collectionId: String,
    folderId: String?,
): String {
    val coll = tree.find { it.id == collectionId } ?: return ""
    if (folderId == null) return coll.name
    val folderLabels = folderPathLabels(coll.folders, folderId) ?: return coll.name
    return (listOf(coll.name) + folderLabels).joinToString(" / ")
}

private fun folderPathLabels(folders: List<UiFolder>, targetId: String): List<String>? {
    for (f in folders) {
        if (f.id == targetId) return listOf(f.name)
        folderPathLabels(f.children, targetId)?.let { tail ->
            return listOf(f.name) + tail
        }
    }
    return null
}
