package db

import java.sql.Connection
import java.sql.Types
import tree.PortableFolder
import tree.PostmanAuth
import tree.UiFolder

internal class FolderTable(private val conn: Connection, private val lock: Any) {

    fun createFolder(collectionId: String, parentFolderId: String?, name: String): String = synchronized(lock) {
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
            if (parentFolderId == null) ps.setNull(3, Types.VARCHAR)
            else ps.setString(3, parentFolderId)
            ps.setString(4, name)
            ps.setInt(5, sort)
            ps.setLong(6, now)
            ps.setLong(7, now)
            ps.executeUpdate()
        }
        return id
    }

    fun renameFolder(id: String, name: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("UPDATE folders SET name = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, now)
            ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    fun deleteFolder(id: String) = synchronized(lock) {
        conn.prepareStatement("DELETE FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    fun countFolderContents(folderId: String): Pair<Int, Int> = synchronized(lock) {
        var folderCount = 0
        var requestCount = 0
        conn.prepareStatement("SELECT id FROM folders WHERE parent_folder_id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    folderCount++
                    val sub = countFolderContents(rs.getString("id"))
                    folderCount += sub.first
                    requestCount += sub.second
                }
            }
        }
        conn.prepareStatement("SELECT COUNT(*) FROM requests WHERE folder_id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (rs.next()) requestCount += rs.getInt(1)
            }
        }
        return folderCount to requestCount
    }

    fun getFolderCollectionId(folderId: String): String? = synchronized(lock) {
        conn.prepareStatement("SELECT collection_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@synchronized null
                return@synchronized rs.getString("collection_id")
            }
        }
    }

    fun getFolderAuth(folderId: String): PostmanAuth? = synchronized(lock) {
        conn.prepareStatement("SELECT meta_json FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return@synchronized extractAuthFromMetaJson(rs.getString("meta_json") ?: "{}")
            }
        }
        null
    }

    fun updateFolderAuth(folderId: String, auth: PostmanAuth?) = synchronized(lock) {
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

    fun folderCollectionAndParent(folderId: String): Pair<String, String?>? = synchronized(lock) {
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

    fun getFolderParentFolderId(folderId: String): String? = synchronized(lock) {
        conn.prepareStatement("SELECT parent_folder_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.getString("parent_folder_id").takeUnless { rs.wasNull() }
            }
        }
    }

    fun getParentFolderId(folderId: String): String? = synchronized(lock) {
        conn.prepareStatement("SELECT parent_folder_id FROM folders WHERE id = ?").use { ps ->
            ps.setString(1, folderId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getString("parent_folder_id")
            }
        }
        return null
    }

    // ── Move logic ──────────────────────────────────────────

    data class FolderMoveInfo(val collectionId: String, val parentFolderId: String?)

    fun getFolderMoveInfo(folderId: String): FolderMoveInfo? = synchronized(lock) {
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

    fun isFolderStrictDescendantOf(descendantCandidateId: String, ancestorFolderId: String): Boolean = synchronized(lock) {
        var cur: String? = descendantCandidateId
        while (cur != null) {
            if (cur == ancestorFolderId) return true
            cur = getFolderParentFolderId(cur)
        }
        return false
    }

    fun loadOrderedFolderIds(collectionId: String, parentFolderId: String?): List<String> = synchronized(lock) {
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
        out
    }

    fun reorderFolderChildren(collectionId: String, parentFolderId: String?, orderedIds: List<String>) = synchronized(lock) {
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

    fun moveFolder(folderId: String, newParentFolderId: String?, insertIndex: Int): Boolean = synchronized(lock) {
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

    // ── Sort order ──────────────────────────────────────────

    fun nextFolderSortOrder(collectionId: String, parentFolderId: String?): Int = synchronized(lock) {
        val sql = if (parentFolderId == null) {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM folders WHERE collection_id = ? AND parent_folder_id IS NULL"
        } else {
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM folders WHERE collection_id = ? AND parent_folder_id = ?"
        }
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, collectionId)
            if (parentFolderId != null) ps.setString(2, parentFolderId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    // ── Tree building ───────────────────────────────────────

    fun buildFolderTree(collectionId: String, parentFolderId: String?): List<UiFolder> = synchronized(lock) {
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
        rows.map { row ->
            UiFolder(
                id = row.id,
                name = row.name,
                children = buildFolderTree(collectionId, row.id),
                requests = RequestTable(conn, lock).loadRequestSummariesInFolder(collectionId, row.id),
            )
        }
    }

    private data class UiFolderRow(val id: String, val name: String, val sortOrder: Int)

    // ── Portable (import/export) ────────────────────────────

    fun buildPortableFolderTree(collectionId: String, parentFolderId: String?): List<PortableFolder> = synchronized(lock) {
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
        rows.map { row ->
            PortableFolder(
                name = row.name,
                sortOrder = row.sortOrder,
                metaJson = row.metaJson,
                auth = extractAuthFromMetaJson(row.metaJson),
                folders = buildPortableFolderTree(collectionId, row.id),
                requests = RequestTable(conn, lock).loadPortableRequests(collectionId, row.id),
                id = row.id,
            )
        }
    }

    private data class FolderRow(
        val id: String,
        val name: String,
        val sortOrder: Int,
        val metaJson: String = "{}",
    )

    fun mergePortableFolders(
        collectionId: String,
        parentFolderId: String?,
        folders: List<PortableFolder>,
        now: Long,
        requestTable: RequestTable,
    ) {
        for (pf in folders) {
            val actualId = resolveAndMergeFolder(collectionId, parentFolderId, pf, now)
            mergePortableFolders(collectionId, actualId, pf.folders, now, requestTable)
            requestTable.mergePortableRequestsInFolder(collectionId, actualId, pf.requests, now)
        }
    }

    private fun resolveAndMergeFolder(
        collectionId: String,
        parentFolderId: String?,
        pf: PortableFolder,
        now: Long,
    ): String {
        val want = pf.id?.trim().orEmpty()
        val owner = if (want.isNotEmpty()) getFolderCollectionId(want) else null
        return when {
            want.isNotEmpty() && owner == collectionId -> {
                updateFolderFromPortable(want, collectionId, parentFolderId, pf, now)
                want
            }
            want.isNotEmpty() && owner == null -> {
                insertFolderWithId(want, collectionId, parentFolderId, pf, now)
                want
            }
            else -> {
                val nid = newId()
                insertFolderWithId(nid, collectionId, parentFolderId, pf, now)
                nid
            }
        }
    }

    private fun updateFolderFromPortable(
        folderId: String,
        collectionId: String,
        parentFolderId: String?,
        pf: PortableFolder,
        now: Long,
    ) {
        val metaJson = mergeAuthIntoMetaJson(pf.metaJson, pf.auth)
        conn.prepareStatement(
            """
            UPDATE folders SET name = ?, parent_folder_id = ?, sort_order = ?, updated_at = ?, meta_json = ?
            WHERE id = ? AND collection_id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, pf.name)
            if (parentFolderId == null) ps.setNull(2, Types.VARCHAR) else ps.setString(2, parentFolderId)
            ps.setInt(3, pf.sortOrder)
            ps.setLong(4, now)
            ps.setString(5, metaJson)
            ps.setString(6, folderId)
            ps.setString(7, collectionId)
            ps.executeUpdate()
        }
    }

    private fun insertFolderWithId(
        folderId: String,
        collectionId: String,
        parentFolderId: String?,
        pf: PortableFolder,
        now: Long,
    ) {
        val metaJson = mergeAuthIntoMetaJson(pf.metaJson, pf.auth)
        conn.prepareStatement(
            """
            INSERT INTO folders (id, collection_id, parent_folder_id, name, sort_order, created_at, updated_at, meta_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, folderId)
            ps.setString(2, collectionId)
            if (parentFolderId == null) ps.setNull(3, Types.VARCHAR) else ps.setString(3, parentFolderId)
            ps.setString(4, pf.name)
            ps.setInt(5, pf.sortOrder)
            ps.setLong(6, now)
            ps.setLong(7, now)
            ps.setString(8, metaJson)
            ps.executeUpdate()
        }
    }

    fun insertPortableFolders(
        collectionId: String,
        parentFolderId: String?,
        folders: List<PortableFolder>,
        now: Long,
        requestTable: RequestTable,
    ) {
        for (pf in folders) {
            val fid = pf.id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
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
            insertPortableFolders(collectionId, fid, pf.folders, now, requestTable)
            for (req in pf.requests) {
                requestTable.insertPortableRequest(collectionId, fid, req, now)
            }
        }
    }
}
