import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class CollectionRepository(dbPath: Path) : AutoCloseable {

    private val conn: Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")

    init {
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("PRAGMA journal_mode = WAL")
        }
        CollectionDatabase.migrate(conn)
        ensureDefaultData()
    }

    override fun close() {
        conn.close()
    }

    fun loadTree(): List<UiCollection> {
        val collections = mutableListOf<Triple<String, String, Int>>()
        conn.prepareStatement(
            "SELECT id, name, sort_order FROM collections ORDER BY sort_order ASC, name ASC"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    collections += Triple(rs.getString("id"), rs.getString("name"), rs.getInt("sort_order"))
                }
            }
        }
        return collections.map { (id, name, _) ->
            UiCollection(
                id = id,
                name = name,
                folders = buildFolderTree(id, null),
                rootRequests = loadRequestSummariesInFolder(id, null),
            )
        }
    }

    fun getRequest(id: String): StoredHttpRequest? {
        conn.prepareStatement(
            """
            SELECT id, collection_id, folder_id, name, method, url, headers_text, body_text, meta_json
            FROM requests WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return StoredHttpRequest(
                    id = rs.getString("id"),
                    collectionId = rs.getString("collection_id"),
                    folderId = rs.getString("folder_id").takeUnless { rs.wasNull() },
                    name = rs.getString("name"),
                    method = rs.getString("method"),
                    url = rs.getString("url"),
                    headersText = rs.getString("headers_text"),
                    bodyText = rs.getString("body_text"),
                    metaJson = rs.getString("meta_json"),
                )
            }
        }
    }

    fun saveRequestEditorFields(
        id: String,
        method: String,
        url: String,
        headersText: String,
        bodyText: String,
    ) {
        val now = System.currentTimeMillis()
        conn.prepareStatement(
            """
            UPDATE requests SET method = ?, url = ?, headers_text = ?, body_text = ?, updated_at = ?
            WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, method)
            ps.setString(2, url)
            ps.setString(3, headersText)
            ps.setString(4, bodyText)
            ps.setLong(5, now)
            ps.setString(6, id)
            ps.executeUpdate()
        }
    }

    fun createCollection(name: String): String {
        val id = newId()
        val now = System.currentTimeMillis()
        val sort = nextCollectionSortOrder()
        conn.prepareStatement(
            """
            INSERT INTO collections (id, name, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, ?, '{}')
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, name)
            ps.setInt(3, sort)
            ps.setLong(4, now)
            ps.setLong(5, now)
            ps.executeUpdate()
        }
        return id
    }

    fun createFolder(collectionId: String, parentFolderId: String?, name: String): String {
        val id = newId()
        val now = System.currentTimeMillis()
        val sort = nextFolderSortOrder(collectionId, parentFolderId)
        conn.prepareStatement(
            """
            INSERT INTO folders (id, collection_id, parent_folder_id, name, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, '{}')
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, collectionId)
            if (parentFolderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
            else ps.setString(3, parentFolderId)
            ps.setString(4, name)
            ps.setInt(5, sort)
            ps.setLong(6, now)
            ps.setLong(7, now)
            ps.executeUpdate()
        }
        return id
    }

    fun createRequest(collectionId: String, folderId: String?, name: String): String {
        val id = newId()
        val now = System.currentTimeMillis()
        val sort = nextRequestSortOrder(collectionId, folderId)
        conn.prepareStatement(
            """
            INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, body_text, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, 'GET', 'https://httpbin.org/get', 'Content-Type: application/json\nAccept: application/json', '{\n}', ?, ?, ?, '{}')
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, collectionId)
            if (folderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
            else ps.setString(3, folderId)
            ps.setString(4, name)
            ps.setInt(5, sort)
            ps.setLong(6, now)
            ps.setLong(7, now)
            ps.executeUpdate()
        }
        return id
    }

    fun renameCollection(id: String, name: String) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("UPDATE collections SET name = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, now)
            ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    fun renameFolder(id: String, name: String) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("UPDATE folders SET name = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, now)
            ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    fun renameRequest(id: String, name: String) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("UPDATE requests SET name = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, now)
            ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    fun deleteCollection(id: String) {
        conn.prepareStatement("DELETE FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    fun deleteFolder(id: String) {
        conn.prepareStatement("DELETE FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    fun deleteRequest(id: String) {
        conn.prepareStatement("DELETE FROM requests WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    fun resolveFolderContext(selection: TreeSelection): Pair<String, String?>? {
        return when (selection) {
            is TreeSelection.Collection -> selection.id to null
            is TreeSelection.Folder -> folderCollectionAndParent(selection.id)
            is TreeSelection.Request -> requestCollectionAndFolder(selection.id)
        }
    }

    /** 新建文件夹：(collectionId, parentFolderId)，parent 为 null 表示集合根下。选中文件夹时在选中项下建子文件夹。 */
    fun newFolderTarget(selection: TreeSelection?): Pair<String, String?>? {
        if (selection == null) return null
        return when (selection) {
            is TreeSelection.Collection -> selection.id to null
            is TreeSelection.Folder -> {
                val cid = getFolderCollectionId(selection.id) ?: return null
                cid to selection.id
            }
            is TreeSelection.Request -> {
                val (cid, reqFolderId) = requestCollectionAndFolder(selection.id) ?: return null
                if (reqFolderId == null) cid to null
                else {
                    val parentOfReqFolder = getFolderParentFolderId(reqFolderId)
                    cid to parentOfReqFolder
                }
            }
        }
    }

    /** 新建请求：与选中请求同目录；选中集合/文件夹时在其下创建。 */
    fun newRequestTarget(selection: TreeSelection?): Pair<String, String?>? {
        if (selection == null) return null
        return when (selection) {
            is TreeSelection.Collection -> selection.id to null
            is TreeSelection.Folder -> {
                val cid = getFolderCollectionId(selection.id) ?: return null
                cid to selection.id
            }
            is TreeSelection.Request -> requestCollectionAndFolder(selection.id)
        }
    }

    fun getFolderCollectionId(folderId: String): String? {
        conn.prepareStatement("SELECT collection_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.getString("collection_id")
            }
        }
    }

    private fun getFolderParentFolderId(folderId: String): String? {
        conn.prepareStatement("SELECT parent_folder_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.getString("parent_folder_id").takeUnless { rs.wasNull() }
            }
        }
    }

    private fun folderCollectionAndParent(folderId: String): Pair<String, String?>? {
        conn.prepareStatement("SELECT collection_id, parent_folder_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val cid = rs.getString("collection_id")
                val pid = rs.getString("parent_folder_id").takeUnless { rs.wasNull() }
                return cid to pid
            }
        }
    }

    private fun requestCollectionAndFolder(requestId: String): Pair<String, String?>? {
        conn.prepareStatement("SELECT collection_id, folder_id FROM requests WHERE id = ?").use { ps ->
            ps.setString(1, requestId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val cid = rs.getString("collection_id")
                val fid = rs.getString("folder_id").takeUnless { rs.wasNull() }
                return cid to fid
            }
        }
    }

    /** 导出为与存储无关的结构，供 Postman / OpenAPI 等适配层序列化。 */
    fun exportPortableCollection(collectionId: String): PortableCollection? {
        val row = getCollectionRow(collectionId) ?: return null
        return PortableCollection(
            name = row.name,
            collectionMetaJson = row.metaJson,
            folders = buildPortableFolderTree(collectionId, null),
            rootRequests = loadPortableRequests(collectionId, null),
        )
    }

    /** 用于导入：新建一个集合并写入整棵树（事务内）；不覆盖已有集合。 */
    fun importAsNewCollection(portable: PortableCollection, collectionName: String? = null) {
        conn.autoCommit = false
        try {
            val collId = newId()
            val now = System.currentTimeMillis()
            val name = collectionName ?: portable.name
            conn.prepareStatement(
                """
                INSERT INTO collections (id, name, sort_order, created_at, updated_at, meta_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, collId)
                ps.setString(2, name)
                ps.setInt(3, nextCollectionSortOrder())
                ps.setLong(4, now)
                ps.setLong(5, now)
                ps.setString(6, portable.collectionMetaJson.ifBlank { "{}" })
                ps.executeUpdate()
            }
            insertPortableFolders(collId, null, portable.folders, now)
            for (req in portable.rootRequests) {
                insertPortableRequest(collId, null, req, now)
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    private data class CollectionRow(val name: String, val metaJson: String)

    private fun getCollectionRow(id: String): CollectionRow? {
        conn.prepareStatement("SELECT name, meta_json FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return CollectionRow(rs.getString("name"), rs.getString("meta_json"))
            }
        }
    }

    private fun buildPortableFolderTree(collectionId: String, parentFolderId: String?): List<PortableFolder> {
        val rows = mutableListOf<FolderRow>()
        val sql = if (parentFolderId == null) {
            "SELECT id, name, sort_order, meta_json FROM folders WHERE collection_id = ? AND parent_folder_id IS NULL ORDER BY sort_order ASC, name ASC"
        } else {
            "SELECT id, name, sort_order, meta_json FROM folders WHERE collection_id = ? AND parent_folder_id = ? ORDER BY sort_order ASC, name ASC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (parentFolderId != null) ps.setString(2, parentFolderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += FolderRow(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        sortOrder = rs.getInt("sort_order"),
                        metaJson = rs.getString("meta_json"),
                    )
                }
            }
        }
        return rows.map { row ->
            PortableFolder(
                name = row.name,
                sortOrder = row.sortOrder,
                metaJson = row.metaJson,
                folders = buildPortableFolderTree(collectionId, row.id),
                requests = loadPortableRequests(collectionId, row.id),
            )
        }
    }

    private fun loadPortableRequests(collectionId: String, folderId: String?): List<PortableRequest> {
        val out = mutableListOf<PortableRequest>()
        val sql = if (folderId == null) {
            """
            SELECT name, method, url, headers_text, body_text, sort_order, meta_json
            FROM requests WHERE collection_id = ? AND folder_id IS NULL
            ORDER BY sort_order ASC, name ASC
            """.trimIndent()
        } else {
            """
            SELECT name, method, url, headers_text, body_text, sort_order, meta_json
            FROM requests WHERE collection_id = ? AND folder_id = ?
            ORDER BY sort_order ASC, name ASC
            """.trimIndent()
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (folderId != null) ps.setString(2, folderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += PortableRequest(
                        name = rs.getString("name"),
                        method = rs.getString("method"),
                        url = rs.getString("url"),
                        headersText = rs.getString("headers_text"),
                        bodyText = rs.getString("body_text"),
                        sortOrder = rs.getInt("sort_order"),
                        metaJson = rs.getString("meta_json"),
                    )
                }
            }
        }
        return out
    }

    private data class FolderRow(
        val id: String,
        val name: String,
        val sortOrder: Int,
        val metaJson: String = "{}",
    )

    private fun insertPortableFolders(
        collectionId: String,
        parentFolderId: String?,
        folders: List<PortableFolder>,
        now: Long,
    ) {
        for (pf in folders) {
            val fid = newId()
            conn.prepareStatement(
                """
                INSERT INTO folders (id, collection_id, parent_folder_id, name, sort_order, created_at, updated_at, meta_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, fid)
                ps.setString(2, collectionId)
                if (parentFolderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
                else ps.setString(3, parentFolderId)
                ps.setString(4, pf.name)
                ps.setInt(5, pf.sortOrder)
                ps.setLong(6, now)
                ps.setLong(7, now)
                ps.setString(8, pf.metaJson.ifBlank { "{}" })
                ps.executeUpdate()
            }
            insertPortableFolders(collectionId, fid, pf.folders, now)
            for (req in pf.requests) {
                insertPortableRequest(collectionId, fid, req, now)
            }
        }
    }

    private fun insertPortableRequest(
        collectionId: String,
        folderId: String?,
        pr: PortableRequest,
        now: Long,
    ) {
        conn.prepareStatement(
            """
            INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, body_text, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, newId())
            ps.setString(2, collectionId)
            if (folderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
            else ps.setString(3, folderId)
            ps.setString(4, pr.name)
            ps.setString(5, pr.method)
            ps.setString(6, pr.url)
            ps.setString(7, pr.headersText)
            ps.setString(8, pr.bodyText)
            ps.setInt(9, pr.sortOrder)
            ps.setLong(10, now)
            ps.setLong(11, now)
            ps.setString(12, pr.metaJson.ifBlank { "{}" })
            ps.executeUpdate()
        }
    }

    private fun buildFolderTree(collectionId: String, parentFolderId: String?): List<UiFolder> {
        val rows = mutableListOf<UiFolderRow>()
        val sql = if (parentFolderId == null) {
            "SELECT id, name, sort_order FROM folders WHERE collection_id = ? AND parent_folder_id IS NULL ORDER BY sort_order ASC, name ASC"
        } else {
            "SELECT id, name, sort_order FROM folders WHERE collection_id = ? AND parent_folder_id = ? ORDER BY sort_order ASC, name ASC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (parentFolderId != null) ps.setString(2, parentFolderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += UiFolderRow(rs.getString("id"), rs.getString("name"), rs.getInt("sort_order"))
                }
            }
        }
        return rows.map { row ->
            UiFolder(
                id = row.id,
                name = row.name,
                children = buildFolderTree(collectionId, row.id),
                requests = loadRequestSummariesInFolder(collectionId, row.id),
            )
        }
    }

    private fun loadRequestSummariesInFolder(collectionId: String, folderId: String?): List<UiRequestSummary> {
        val out = mutableListOf<UiRequestSummary>()
        val sql = if (folderId == null) {
            "SELECT id, name, method FROM requests WHERE collection_id = ? AND folder_id IS NULL ORDER BY sort_order ASC, name ASC"
        } else {
            "SELECT id, name, method FROM requests WHERE collection_id = ? AND folder_id = ? ORDER BY sort_order ASC, name ASC"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (folderId != null) ps.setString(2, folderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += UiRequestSummary(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        method = rs.getString("method"),
                    )
                }
            }
        }
        return out
    }

    private data class UiFolderRow(val id: String, val name: String, val sortOrder: Int)

    private fun nextCollectionSortOrder(): Int {
        return conn.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM collections").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun nextFolderSortOrder(collectionId: String, parentFolderId: String?): Int {
        val sql = if (parentFolderId == null) {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM folders WHERE collection_id = ? AND parent_folder_id IS NULL"
        } else {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM folders WHERE collection_id = ? AND parent_folder_id = ?"
        }
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (parentFolderId != null) ps.setString(2, parentFolderId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun nextRequestSortOrder(collectionId: String, folderId: String?): Int {
        val sql = if (folderId == null) {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM requests WHERE collection_id = ? AND folder_id IS NULL"
        } else {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM requests WHERE collection_id = ? AND folder_id = ?"
        }
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (folderId != null) ps.setString(2, folderId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun ensureDefaultData() {
        val count = conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM collections").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        if (count > 0) return
        val cid = createCollection("默认集合")
        createRequest(cid, null, "新请求")
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
