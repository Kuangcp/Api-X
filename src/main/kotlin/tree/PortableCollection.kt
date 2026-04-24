package tree

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
    val auth: PostmanAuth? = null,
    val folders: List<PortableFolder> = emptyList(),
    val rootRequests: List<PortableRequest> = emptyList(),
    /** 本应用导出时写入；用于 data 目录按 id 合并。外部 Postman 文件通常无此字段。 */
    val id: String? = null,
)

data class PortableFolder(
    val name: String,
    val sortOrder: Int = 0,
    val metaJson: String = "{}",
    val auth: PostmanAuth? = null,
    val folders: List<PortableFolder> = emptyList(),
    val requests: List<PortableRequest> = emptyList(),
    /** 与 DB `folders.id` 对应；导出时写入 Postman item。 */
    val id: String? = null,
)

data class PortableRequest(
    val name: String,
    val method: String,
    val url: String,
    val headersText: String = "",
    val paramsText: String = "",
    val bodyText: String = "",
    val sortOrder: Int = 0,
    val metaJson: String = "{}",
    val auth: PostmanAuth? = null,
    /** 与 DB `requests.id` 对应。 */
    val id: String? = null,
)
