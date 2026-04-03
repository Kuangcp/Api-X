/**
 * 与具体数据库表解耦的中间模型，便于日后对接：
 * - Postman Collection v2.1（item / folder 嵌套、request 对象）
 * - OpenAPI 3.x（paths / operations，可映射为扁平或分层 folder）
 *
 * 字段刻意贴近当前编辑器形态（method、url、headers 文本、body 文本），
 * 扩展信息放入 meta_json（如 postman.id、x-openapi 等）由导入器写入。
 */
data class PortableCollection(
    val name: String,
    val collectionMetaJson: String = "{}",
    val folders: List<PortableFolder> = emptyList(),
    val rootRequests: List<PortableRequest> = emptyList(),
)

data class PortableFolder(
    val name: String,
    val sortOrder: Int = 0,
    val metaJson: String = "{}",
    val folders: List<PortableFolder> = emptyList(),
    val requests: List<PortableRequest> = emptyList(),
)

data class PortableRequest(
    val name: String,
    val method: String,
    val url: String,
    val headersText: String = "",
    val bodyText: String = "",
    val sortOrder: Int = 0,
    val metaJson: String = "{}",
)
