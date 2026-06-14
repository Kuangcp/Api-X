package db

import java.sql.Connection
import java.sql.Types
import tree.GlobalSearchRequestRow
import tree.PortableRequest
import tree.PostmanAuth
import tree.StoredHttpRequest
import tree.UiRequestSummary

internal class RequestTable(private val conn: Connection, private val lock: Any) {

    fun getRequest(id: String): StoredHttpRequest? = synchronized(lock) {
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

    fun loadAllRequestsForGlobalSearch(): List<GlobalSearchRequestRow> = synchronized(lock) {
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
    ) = synchronized(lock) {
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

    fun createRequest(collectionId: String, folderId: String?, name: String): String = synchronized(lock) {
        val id = newId()
        val now = System.currentTimeMillis()
        val sort = nextRequestSortOrder(collectionId, folderId)
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

    fun duplicateRequestBelow(sourceId: String): String? = synchronized(lock) {
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
            shiftRequestsDown(row.collectionId, row.folderId, insertAt, now)
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

    fun createRequestBelow(sourceId: String): String? = synchronized(lock) {
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
            shiftRequestsDown(row.collectionId, row.folderId, insertAt, now)
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

    private fun shiftRequestsDown(collectionId: String, folderId: String?, insertAt: Int, now: Long) {
        if (folderId == null) {
            conn.prepareStatement(
                """
                UPDATE requests SET sort_order = sort_order + 1, updated_at = ?
                WHERE collection_id = ? AND folder_id IS NULL AND sort_order >= ?
                """.trimIndent()
            ).use { ps ->
                ps.setLong(1, now)
                ps.setString(2, collectionId)
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
                ps.setString(2, collectionId)
                ps.setString(3, folderId)
                ps.setInt(4, insertAt)
                ps.executeUpdate()
            }
        }
    }

    fun renameRequest(id: String, name: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("UPDATE requests SET name = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, now)
            ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    fun deleteRequest(id: String) = synchronized(lock) {
        conn.prepareStatement("DELETE FROM requests WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    // ── Move logic ──────────────────────────────────────────

    data class RequestMoveInfo(val collectionId: String, val folderId: String?)

    fun getRequestMoveInfo(requestId: String): RequestMoveInfo? = synchronized(lock) {
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

    fun getRequestCollectionId(requestId: String): String? = synchronized(lock) {
        conn.prepareStatement("SELECT collection_id FROM requests WHERE id = ?").use { ps ->
            ps.setString(1, requestId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getString("collection_id")
            }
        }
        return null
    }

    fun loadOrderedRequestIds(collectionId: String, folderId: String?): List<String> = synchronized(lock) {
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
        out
    }

    fun reorderRequestChildren(collectionId: String, folderId: String?, orderedIds: List<String>) = synchronized(lock) {
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

    fun requestCollectionAndFolder(requestId: String): Pair<String, String?>? = synchronized(lock) {
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

    fun moveRequest(requestId: String, newFolderId: String?, insertIndex: Int): Boolean = synchronized(lock) {
        val info = getRequestMoveInfo(requestId) ?: return false
        if (newFolderId != null) {
            val folderTable = FolderTable(conn, lock)
            if (folderTable.getFolderCollectionId(newFolderId) != info.collectionId) return false
        }
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

    // ── Sort order ──────────────────────────────────────────

    fun nextRequestSortOrder(collectionId: String, folderId: String?): Int = synchronized(lock) {
        val sql = if (folderId == null) {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM requests WHERE collection_id = ? AND folder_id IS NULL"
        } else {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM requests WHERE collection_id = ? AND folder_id = ?"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (folderId != null) ps.setString(2, folderId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    // ── Tree summaries ──────────────────────────────────────

    fun loadRequestSummariesInFolder(collectionId: String, folderId: String?): List<UiRequestSummary> = synchronized(lock) {
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

    // ── Portable (import/export) ────────────────────────────

    fun loadPortableRequests(collectionId: String, folderId: String?): List<PortableRequest> = synchronized(lock) {
        val out = mutableListOf<PortableRequest>()
        val sql = if (folderId == null) {
            """
            SELECT id, name, method, url, headers_text, params_text, body_text, sort_order, meta_json
            FROM requests WHERE collection_id = ? AND folder_id IS NULL
            ORDER BY sort_order ASC, name ASC
            """.trimIndent()
        } else {
            """
            SELECT id, name, method, url, headers_text, params_text, body_text, sort_order, meta_json
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
                        id = rs.getString("id"),
                    )
                }
            }
        }
        return out
    }

    fun mergePortableRequestsInFolder(
        collectionId: String,
        folderId: String?,
        requests: List<PortableRequest>,
        now: Long,
    ) {
        for (pr in requests) {
            val want = pr.id?.trim().orEmpty()
            val owner = if (want.isNotEmpty()) getRequestCollectionId(want) else null
            when {
                want.isNotEmpty() && owner == collectionId -> {
                    updateRequestFromPortable(want, collectionId, folderId, pr, now)
                }
                want.isNotEmpty() && owner == null -> {
                    insertPortableRequest(collectionId, folderId, pr, now)
                }
                else -> {
                    val nid = newId()
                    insertPortableRequest(collectionId, folderId, pr.copy(id = nid), now)
                }
            }
        }
    }

    private fun updateRequestFromPortable(
        requestId: String,
        collectionId: String,
        folderId: String?,
        pr: PortableRequest,
        now: Long,
    ) {
        val metaJson = mergeAuthIntoMetaJson(pr.metaJson, pr.auth)
        conn.prepareStatement(
            """
            UPDATE requests SET collection_id = ?, folder_id = ?, name = ?, method = ?, url = ?,
            headers_text = ?, params_text = ?, body_text = ?, sort_order = ?, updated_at = ?, meta_json = ?
            WHERE id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, collectionId)
            if (folderId == null) ps.setNull(2, Types.VARCHAR) else ps.setString(2, folderId)
            ps.setString(3, pr.name)
            ps.setString(4, pr.method)
            ps.setString(5, pr.url)
            ps.setString(6, pr.headersText)
            ps.setString(7, pr.paramsText)
            ps.setString(8, pr.bodyText)
            ps.setInt(9, pr.sortOrder)
            ps.setLong(10, now)
            ps.setString(11, metaJson)
            ps.setString(12, requestId)
            ps.executeUpdate()
        }
    }

    fun insertPortableRequest(
        collectionId: String,
        folderId: String?,
        pr: PortableRequest,
        now: Long,
    ) {
        val metaJson = mergeAuthIntoMetaJson(pr.metaJson, pr.auth)
        val requestId = pr.id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
        conn.prepareStatement(
            """
            INSERT INTO requests (id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, requestId)
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

    // ── Auth resolution ─────────────────────────────────────

    fun resolveEffectiveAuth(requestId: String, folderTable: FolderTable, collectionTable: CollectionTable): PostmanAuth? = synchronized(lock) {
        val req = getRequest(requestId) ?: return@synchronized null
        val reqAuth = req.auth
        if (reqAuth != null && reqAuth.type != "inherit") return@synchronized reqAuth

        var currentFolderId = req.folderId
        while (currentFolderId != null) {
            val folderAuth = folderTable.getFolderAuth(currentFolderId)
            if (folderAuth != null && folderAuth.type != "inherit") return@synchronized folderAuth
            currentFolderId = folderTable.getParentFolderId(currentFolderId)
        }

        collectionTable.getCollectionAuth(req.collectionId)
    }
}
