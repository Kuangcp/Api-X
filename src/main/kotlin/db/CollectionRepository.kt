package db

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import tree.PortableCollection
import tree.PortableFolder
import tree.PortableRequest
import tree.PostmanAuth
import tree.StoredHttpRequest
import tree.TreeDragPayload
import tree.TreeDropTarget
import tree.TreeSelection
import tree.GlobalSearchRequestRow
import tree.UiCollection
import tree.UiFolder
import tree.UiRequestSummary

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
            SELECT id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, meta_json
            FROM requests WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val metaJson = rs.getString("meta_json") ?: "{}"
                return StoredHttpRequest(
                    id = rs.getString("id"),
                    collectionId = rs.getString("collection_id"),
                    folderId = rs.getString("folder_id").takeUnless { rs.wasNull() },
                    name = rs.getString("name"),
                    method = rs.getString("method"),
                    url = rs.getString("url"),
                    headersText = rs.getString("headers_text"),
                    paramsText = rs.getString("params_text"),
                    bodyText = rs.getString("body_text"),
                    metaJson = metaJson,
                    auth = extractAuthFromMetaJson(metaJson),
                )
            }
        }
    }

    fun loadAllRequestsForGlobalSearch(): List<GlobalSearchRequestRow> {
        val out = mutableListOf<GlobalSearchRequestRow>()
        conn.prepareStatement(
            """
            SELECT id, collection_id, folder_id, name, method, url, headers_text, body_text
            FROM requests
            ORDER BY collection_id, folder_id, name
            """.trimIndent(),
        ).use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += GlobalSearchRequestRow(
                        id = rs.getString("id"),
                        collectionId = rs.getString("collection_id"),
                        folderId = rs.getString("folder_id").takeUnless { rs.wasNull() },
                        name = rs.getString("name"),
                        method = rs.getString("method"),
                        url = rs.getString("url") ?: "",
                        headersText = rs.getString("headers_text") ?: "",
                        bodyText = rs.getString("body_text") ?: "",
                    )
                }
            }
        }
        return out
    }

    fun saveRequestEditorFields(
        id: String,
        method: String,
        url: String,
        headersText: String,
        paramsText: String,
        bodyText: String,
        auth: PostmanAuth?,
    ) {
        val now = System.currentTimeMillis()
        val oldMetaJson = getRequestMetaJson(id)
        val newMetaJson = mergeAuthIntoMetaJson(oldMetaJson, auth)

        conn.prepareStatement(
            """
            UPDATE requests SET method = ?, url = ?, headers_text = ?, params_text = ?, body_text = ?, meta_json = ?, updated_at = ?
            WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, method)
            ps.setString(2, url)
            ps.setString(3, headersText)
            ps.setString(4, paramsText)
            ps.setString(5, bodyText)
            ps.setString(6, newMetaJson)
            ps.setLong(7, now)
            ps.setString(8, id)
            ps.executeUpdate()
        }
    }

    private fun getRequestMetaJson(id: String): String {
        conn.prepareStatement("SELECT meta_json FROM requests WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getString("meta_json") ?: "{}"
            }
        }
        return "{}"
    }

    private fun mergeAuthIntoMetaJson(oldMetaJson: String, auth: PostmanAuth?): String {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
        val meta = try {
            json.decodeFromString<JsonObject>(oldMetaJson.ifBlank { "{}" }).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        }
        if (auth == null) {
            meta.remove("auth")
        } else {
            meta["auth"] = json.encodeToJsonElement(auth)
        }
        return json.encodeToString(JsonObject(meta))
    }

    private fun extractAuthFromMetaJson(metaJson: String): PostmanAuth? {
        val json = Json { ignoreUnknownKeys = true }
        return try {
            val meta = json.decodeFromString<JsonObject>(metaJson.ifBlank { "{}" })
            meta["auth"]?.let { json.decodeFromJsonElement(PostmanAuth.serializer(), it) }
        } catch (e: Exception) {
            null
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
        // 须用 Kotlin 普通字符串里的真实换行；勿写在 """ """ SQL 里，否则 \n 会按字面存入库
        val defaultHeaders = "Content-Type: application/x-www-form-urlencoded\nAccept: */*"
        val defaultBody = "key: value"
        conn.prepareStatement(
            """
            INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, 'GET', 'https://httpbin.org/get', ?, '', ?, ?, ?, ?, '{}')
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, id)
            ps.setString(2, collectionId)
            if (folderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
            else ps.setString(3, folderId)
            ps.setString(4, name)
            ps.setString(5, defaultHeaders)
            ps.setString(6, defaultBody)
            ps.setInt(7, sort)
            ps.setLong(8, now)
            ps.setLong(9, now)
            ps.executeUpdate()
        }
        return id
    }

    /**
     * 在同一集合、同一文件夹（含根）内，在原请求下方插入副本；
     * 名称为「原名 + " Copy"」，并复制 method/url/headers/body/meta。
     */
    fun duplicateRequestBelow(sourceId: String): String? {
        val row = conn.prepareStatement(
            """
            SELECT collection_id, folder_id, name, method, url, headers_text, params_text, body_text, meta_json, sort_order
            FROM requests WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, sourceId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                object {
                    val collectionId = rs.getString("collection_id")
                    val folderId = rs.getString("folder_id").takeUnless { rs.wasNull() }
                    val name = rs.getString("name")
                    val method = rs.getString("method")
                    val url = rs.getString("url")
                    val headersText = rs.getString("headers_text")
                    val paramsText = rs.getString("params_text")
                    val bodyText = rs.getString("body_text")
                    val metaJson = rs.getString("meta_json")
                    val sortOrder = rs.getInt("sort_order")
                }
            }
        }
        val newId = newId()
        val newName = "${row.name} Copy"
        val insertAt = row.sortOrder + 1
        val now = System.currentTimeMillis()
        val oldAutoCommit = conn.autoCommit
        return try {
            conn.autoCommit = false
            if (row.folderId == null) {
                conn.prepareStatement(
                    """
                    UPDATE requests SET sort_order = sort_order + 1, updated_at = ?
                    WHERE collection_id = ? AND folder_id IS NULL AND sort_order >= ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, row.collectionId)
                    ps.setInt(3, insertAt)
                    ps.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                    """
                    UPDATE requests SET sort_order = sort_order + 1, updated_at = ?
                    WHERE collection_id = ? AND folder_id = ? AND sort_order >= ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, row.collectionId)
                    ps.setString(3, row.folderId)
                    ps.setInt(4, insertAt)
                    ps.executeUpdate()
                }
            }
            conn.prepareStatement(
                """
                INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, sort_order, created_at, updated_at, meta_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, newId)
                ps.setString(2, row.collectionId)
                if (row.folderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
                else ps.setString(3, row.folderId)
                ps.setString(4, newName)
                ps.setString(5, row.method)
                ps.setString(6, row.url)
                ps.setString(7, row.headersText)
                ps.setString(8, row.paramsText)
                ps.setString(9, row.bodyText)
                ps.setInt(10, insertAt)
                ps.setLong(11, now)
                ps.setLong(12, now)
                ps.setString(13, row.metaJson)
                ps.executeUpdate()
            }
            conn.commit()
            newId
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    /**
     * 在同一集合、同一文件夹（含根）内，在指定请求正下方插入一条新请求；
     * 默认字段与 [createRequest] 一致（GET、示例 URL、默认 headers/body），名称为「新请求」。
     */
    fun createRequestBelow(sourceId: String): String? {
        val row = conn.prepareStatement(
            """
            SELECT collection_id, folder_id, sort_order
            FROM requests WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, sourceId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                object {
                    val collectionId = rs.getString("collection_id")
                    val folderId = rs.getString("folder_id").takeUnless { rs.wasNull() }
                    val sortOrder = rs.getInt("sort_order")
                }
            }
        }
        val newId = newId()
        val insertAt = row.sortOrder + 1
        val now = System.currentTimeMillis()
        val defaultHeaders = "Content-Type: application/x-www-form-urlencoded\nAccept: */*"
        val defaultBody = "key: value"
        val oldAutoCommit = conn.autoCommit
        return try {
            conn.autoCommit = false
            if (row.folderId == null) {
                conn.prepareStatement(
                    """
                    UPDATE requests SET sort_order = sort_order + 1, updated_at = ?
                    WHERE collection_id = ? AND folder_id IS NULL AND sort_order >= ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, row.collectionId)
                    ps.setInt(3, insertAt)
                    ps.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                    """
                    UPDATE requests SET sort_order = sort_order + 1, updated_at = ?
                    WHERE collection_id = ? AND folder_id = ? AND sort_order >= ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, row.collectionId)
                    ps.setString(3, row.folderId)
                    ps.setInt(4, insertAt)
                    ps.executeUpdate()
                }
            }
            conn.prepareStatement(
                """
                INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, sort_order, created_at, updated_at, meta_json)
                VALUES (?, ?, ?, ?, 'GET', 'https://httpbin.org/get', ?, '', ?, ?, ?, ?, '{}')
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, newId)
                ps.setString(2, row.collectionId)
                if (row.folderId == null) ps.setNull(3, java.sql.Types.VARCHAR)
                else ps.setString(3, row.folderId)
                ps.setString(4, "新请求")
                ps.setString(5, defaultHeaders)
                ps.setString(6, defaultBody)
                ps.setInt(7, insertAt)
                ps.setLong(8, now)
                ps.setLong(9, now)
                ps.executeUpdate()
            }
            conn.commit()
            newId
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = oldAutoCommit
        }
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

    /**
     * 在同一集合内移动文件夹：调整 [parent_folder_id] 与同级 [sort_order]。
     * 不可将文件夹拖入自身或其子孙下。
     */
    fun moveFolder(folderId: String, newParentFolderId: String?, insertIndex: Int): Boolean {
        val info = getFolderMoveInfo(folderId) ?: return false
        if (newParentFolderId != null) {
            if (newParentFolderId == folderId) return false
            if (getFolderCollectionId(newParentFolderId) != info.collectionId) return false
            if (isFolderStrictDescendantOf(newParentFolderId, folderId)) return false
        }
        val oldParent = info.parentFolderId
        val coll = info.collectionId
        if (oldParent == newParentFolderId) {
            val s = loadOrderedFolderIds(coll, oldParent).toMutableList()
            if (!s.remove(folderId)) return false
            val idx = insertIndex.coerceIn(0, s.size)
            s.add(idx, folderId)
            reorderFolderChildren(coll, oldParent, s)
            return true
        }
        val oldAutoCommit = conn.autoCommit
        return try {
            conn.autoCommit = false
            val newS = loadOrderedFolderIds(coll, newParentFolderId).toMutableList()
            newS.remove(folderId)
            val idx = insertIndex.coerceIn(0, newS.size)
            newS.add(idx, folderId)
            reorderFolderChildren(coll, newParentFolderId, newS)

            val oldS = loadOrderedFolderIds(coll, oldParent).toMutableList()
            oldS.remove(folderId)
            reorderFolderChildren(coll, oldParent, oldS)

            conn.commit()
            true
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    /** 在同一集合内移动请求（根下 folder_id IS NULL 或某文件夹下）。 */
    fun moveRequest(requestId: String, newFolderId: String?, insertIndex: Int): Boolean {
        val info = getRequestMoveInfo(requestId) ?: return false
        if (newFolderId != null && getFolderCollectionId(newFolderId) != info.collectionId) return false
        val oldFolder = info.folderId
        val coll = info.collectionId
        if (oldFolder == newFolderId) {
            val s = loadOrderedRequestIds(coll, oldFolder).toMutableList()
            if (!s.remove(requestId)) return false
            val idx = insertIndex.coerceIn(0, s.size)
            s.add(idx, requestId)
            reorderRequestChildren(coll, oldFolder, s)
            return true
        }
        val oldAutoCommit = conn.autoCommit
        return try {
            conn.autoCommit = false
            val newS = loadOrderedRequestIds(coll, newFolderId).toMutableList()
            newS.remove(requestId)
            val idx = insertIndex.coerceIn(0, newS.size)
            newS.add(idx, requestId)
            reorderRequestChildren(coll, newFolderId, newS)

            val oldS = loadOrderedRequestIds(coll, oldFolder).toMutableList()
            oldS.remove(requestId)
            reorderRequestChildren(coll, oldFolder, oldS)

            conn.commit()
            true
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    fun applyTreeDrop(payload: TreeDragPayload, target: TreeDropTarget): Boolean {
        return when (payload) {
            is TreeDragPayload.Folder -> when (target) {
                is TreeDropTarget.FolderSlot ->
                    moveFolder(payload.id, target.parentFolderId, target.insertIndex)
                is TreeDropTarget.IntoFolder -> {
                    val n = loadOrderedFolderIds(target.collectionId, target.folderId).size
                    moveFolder(payload.id, target.folderId, n)
                }
                is TreeDropTarget.IntoCollection -> {
                    val info = getFolderMoveInfo(payload.id) ?: return false
                    if (info.collectionId != target.collectionId) return false
                    val n = loadOrderedFolderIds(target.collectionId, null).size
                    moveFolder(payload.id, null, n)
                }
                is TreeDropTarget.RequestSlot -> false
            }
            is TreeDragPayload.Request -> when (target) {
                is TreeDropTarget.RequestSlot ->
                    moveRequest(payload.id, target.folderId, target.insertIndex)
                is TreeDropTarget.IntoFolder -> {
                    val n = loadOrderedRequestIds(target.collectionId, target.folderId).size
                    moveRequest(payload.id, target.folderId, n)
                }
                is TreeDropTarget.IntoCollection -> {
                    val info = getRequestMoveInfo(payload.id) ?: return false
                    if (info.collectionId != target.collectionId) return false
                    val n = loadOrderedRequestIds(target.collectionId, null).size
                    moveRequest(payload.id, null, n)
                }
                is TreeDropTarget.FolderSlot -> false
            }
        }
    }

    private data class FolderMoveInfo(val collectionId: String, val parentFolderId: String?)

    private data class RequestMoveInfo(val collectionId: String, val folderId: String?)

    private fun getFolderMoveInfo(folderId: String): FolderMoveInfo? {
        conn.prepareStatement(
            "SELECT collection_id, parent_folder_id FROM folders WHERE id = ?"
        ).use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val cid = rs.getString("collection_id")
                val pid = rs.getString("parent_folder_id").takeUnless { rs.wasNull() }
                return FolderMoveInfo(cid, pid)
            }
        }
    }

    private fun getRequestMoveInfo(requestId: String): RequestMoveInfo? {
        conn.prepareStatement(
            "SELECT collection_id, folder_id FROM requests WHERE id = ?"
        ).use { ps ->
            ps.setString(1, requestId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                val cid = rs.getString("collection_id")
                val fid = rs.getString("folder_id").takeUnless { rs.wasNull() }
                return RequestMoveInfo(cid, fid)
            }
        }
    }

    /** [ancestorFolderId] 是否是 [folderId] 的子孙（不含自身）。 */
    private fun isFolderStrictDescendantOf(descendantCandidateId: String, ancestorFolderId: String): Boolean {
        var cur: String? = descendantCandidateId
        while (cur != null) {
            if (cur == ancestorFolderId) return true
            cur = getFolderParentFolderId(cur)
        }
        return false
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

    private fun loadOrderedFolderIds(collectionId: String, parentFolderId: String?): List<String> {
        val sql = if (parentFolderId == null) {
            "SELECT id FROM folders WHERE collection_id = ? AND parent_folder_id IS NULL ORDER BY sort_order ASC, name ASC"
        } else {
            "SELECT id FROM folders WHERE collection_id = ? AND parent_folder_id = ? ORDER BY sort_order ASC, name ASC"
        }
        val out = mutableListOf<String>()
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (parentFolderId != null) ps.setString(2, parentFolderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) out += rs.getString("id")
            }
        }
        return out
    }

    private fun loadOrderedRequestIds(collectionId: String, folderId: String?): List<String> {
        val sql = if (folderId == null) {
            "SELECT id FROM requests WHERE collection_id = ? AND folder_id IS NULL ORDER BY sort_order ASC, name ASC"
        } else {
            "SELECT id FROM requests WHERE collection_id = ? AND folder_id = ? ORDER BY sort_order ASC, name ASC"
        }
        val out = mutableListOf<String>()
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (folderId != null) ps.setString(2, folderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) out += rs.getString("id")
            }
        }
        return out
    }

    private fun reorderFolderChildren(collectionId: String, parentFolderId: String?, orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        for ((i, id) in orderedIds.withIndex()) {
            conn.prepareStatement(
                """
                UPDATE folders SET parent_folder_id = ?, sort_order = ?, updated_at = ?
                WHERE id = ? AND collection_id = ?
                """.trimIndent()
            ).use { ps ->
                if (parentFolderId == null) ps.setNull(1, Types.VARCHAR)
                else ps.setString(1, parentFolderId)
                ps.setInt(2, i)
                ps.setLong(3, now)
                ps.setString(4, id)
                ps.setString(5, collectionId)
                ps.executeUpdate()
            }
        }
    }

    private fun reorderRequestChildren(collectionId: String, folderId: String?, orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        for ((i, id) in orderedIds.withIndex()) {
            conn.prepareStatement(
                """
                UPDATE requests SET folder_id = ?, sort_order = ?, updated_at = ?
                WHERE id = ? AND collection_id = ?
                """.trimIndent()
            ).use { ps ->
                if (folderId == null) ps.setNull(1, Types.VARCHAR)
                else ps.setString(1, folderId)
                ps.setInt(2, i)
                ps.setLong(3, now)
                ps.setString(4, id)
                ps.setString(5, collectionId)
                ps.executeUpdate()
            }
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
            auth = extractAuthFromMetaJson(row.metaJson),
            folders = buildPortableFolderTree(collectionId, null),
            rootRequests = loadPortableRequests(collectionId, null),
        )
    }

    /** 用于导入：新建一个集合并写入整棵树（事务内）；不覆盖已有集合。返回新集合 id。 */
    fun importAsNewCollection(portable: PortableCollection, collectionName: String? = null): String {
        conn.autoCommit = false
        try {
            val collId = newId()
            val now = System.currentTimeMillis()
            val name = collectionName ?: portable.name
            val metaJson = mergeAuthIntoMetaJson(portable.collectionMetaJson, portable.auth)
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
                ps.setString(6, metaJson)
                ps.executeUpdate()
            }
            insertPortableFolders(collId, null, portable.folders, now)
            for (req in portable.rootRequests) {
                insertPortableRequest(collId, null, req, now)
            }
            conn.commit()
            return collId
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

    fun resolveEffectiveAuth(requestId: String): PostmanAuth? {
        // 1. Check request itself
        val req = getRequest(requestId) ?: return null
        val reqAuth = req.auth
        if (reqAuth != null && reqAuth.type != "inherit") return reqAuth

        // 2. Check folder (if any)
        var currentFolderId = req.folderId
        while (currentFolderId != null) {
            val folderAuth = getFolderAuth(currentFolderId)
            if (folderAuth != null && folderAuth.type != "inherit") return folderAuth
            currentFolderId = getParentFolderId(currentFolderId)
        }

        // 3. Check collection
        return getCollectionAuth(req.collectionId)
    }

    fun getCollectionAuth(collectionId: String): PostmanAuth? {
        conn.prepareStatement("SELECT meta_json FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, collectionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return extractAuthFromMetaJson(rs.getString("meta_json") ?: "{}")
            }
        }
        return null
    }

    fun updateCollectionAuth(collectionId: String, auth: PostmanAuth?) {
        val oldMeta = conn.prepareStatement("SELECT meta_json FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, collectionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getString("meta_json") ?: "{}" else "{}"
            }
        }
        val newMeta = mergeAuthIntoMetaJson(oldMeta, auth)
        conn.prepareStatement("UPDATE collections SET meta_json = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, newMeta)
            ps.setLong(2, System.currentTimeMillis())
            ps.setString(3, collectionId)
            ps.executeUpdate()
        }
    }

    fun getFolderAuth(folderId: String): PostmanAuth? {
        conn.prepareStatement("SELECT meta_json FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return extractAuthFromMetaJson(rs.getString("meta_json") ?: "{}")
            }
        }
        return null
    }

    fun updateFolderAuth(folderId: String, auth: PostmanAuth?) {
        val oldMeta = conn.prepareStatement("SELECT meta_json FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getString("meta_json") ?: "{}" else "{}"
            }
        }
        val newMeta = mergeAuthIntoMetaJson(oldMeta, auth)
        conn.prepareStatement("UPDATE folders SET meta_json = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, newMeta)
            ps.setLong(2, System.currentTimeMillis())
            ps.setString(3, folderId)
            ps.executeUpdate()
        }
    }

    private fun getParentFolderId(folderId: String): String? {
        conn.prepareStatement("SELECT parent_folder_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getString("parent_folder_id")
            }
        }
        return null
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
                auth = extractAuthFromMetaJson(row.metaJson),
                folders = buildPortableFolderTree(collectionId, row.id),
                requests = loadPortableRequests(collectionId, row.id),
            )
        }
    }

    private fun loadPortableRequests(collectionId: String, folderId: String?): List<PortableRequest> {
        val out = mutableListOf<PortableRequest>()
        val sql = if (folderId == null) {
            """
            SELECT name, method, url, headers_text, params_text, body_text, sort_order, meta_json
            FROM requests WHERE collection_id = ? AND folder_id IS NULL
            ORDER BY sort_order ASC, name ASC
            """.trimIndent()
        } else {
            """
            SELECT name, method, url, headers_text, params_text, body_text, sort_order, meta_json
            FROM requests WHERE collection_id = ? AND folder_id = ?
            ORDER BY sort_order ASC, name ASC
            """.trimIndent()
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (folderId != null) ps.setString(2, folderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val metaJson = rs.getString("meta_json") ?: "{}"
                    out += PortableRequest(
                        name = rs.getString("name"),
                        method = rs.getString("method"),
                        url = rs.getString("url"),
                        headersText = rs.getString("headers_text"),
                        paramsText = rs.getString("params_text"),
                        bodyText = rs.getString("body_text"),
                        sortOrder = rs.getInt("sort_order"),
                        metaJson = metaJson,
                        auth = extractAuthFromMetaJson(metaJson),
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
            val metaJson = mergeAuthIntoMetaJson(pf.metaJson, pf.auth)
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
                ps.setString(8, metaJson)
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
        val metaJson = mergeAuthIntoMetaJson(pr.metaJson, pr.auth)
        conn.prepareStatement(
            """
            INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.setString(8, pr.paramsText)
            ps.setString(9, pr.bodyText)
            ps.setInt(10, pr.sortOrder)
            ps.setLong(11, now)
            ps.setLong(12, now)
            ps.setString(13, metaJson)
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
