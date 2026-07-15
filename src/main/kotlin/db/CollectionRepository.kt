package db

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import openapi.openApiSyncKey
import tree.PortableCollection
import tree.PortableFolder
import tree.PortableRequest
import tree.TreeDragPayload
import tree.TreeDropTarget
import tree.TreeSelection

private val repositoryMetaJson = Json { ignoreUnknownKeys = true }

class CollectionRepository(dbPath: Path) : AutoCloseable {

    private val lock = Any()
    private val conn: Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")

    internal val collectionTable = CollectionTable(conn, lock)
    internal val folderTable = FolderTable(conn, lock)
    internal val requestTable = RequestTable(conn, lock)

    init {
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("PRAGMA journal_mode = WAL")
            st.execute("PRAGMA busy_timeout = 5000")
            st.execute("PRAGMA synchronous = NORMAL")
            st.execute("PRAGMA cache_size = -8000")
        }
        CollectionDatabase.migrate(conn)
        ensureDefaultData()
    }

    override fun close() = synchronized(lock) { conn.close() }

    // ── Delegated public API ────────────────────────────────

    fun loadTree(): List<tree.UiCollection> = synchronized(lock) {
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
        collections.map { (id, name, _) ->
            tree.UiCollection(
                id = id,
                name = name,
                folders = folderTable.buildFolderTree(id, null),
                rootRequests = requestTable.loadRequestSummariesInFolder(id, null),
                openApiSourceUrl = collectionTable.getCollectionOpenApiSource(id),
            )
        }
    }

    fun getRequest(id: String) = requestTable.getRequest(id)
    fun loadAllRequestsForGlobalSearch() = requestTable.loadAllRequestsForGlobalSearch()
    fun saveRequestEditorFields(
        id: String, method: String, url: String, headersText: String,
        paramsText: String, bodyText: String, auth: tree.PostmanAuth?,
    ) = requestTable.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)

    fun createCollection(name: String) = collectionTable.createCollection(name)
    fun createFolder(collectionId: String, parentFolderId: String?, name: String) = folderTable.createFolder(collectionId, parentFolderId, name)
    fun createRequest(collectionId: String, folderId: String?, name: String) = requestTable.createRequest(collectionId, folderId, name)
    fun duplicateRequestBelow(sourceId: String) = requestTable.duplicateRequestBelow(sourceId)
    fun createRequestBelow(sourceId: String) = requestTable.createRequestBelow(sourceId)

    fun renameCollection(id: String, name: String) = collectionTable.renameCollection(id, name)
    fun renameFolder(id: String, name: String) = folderTable.renameFolder(id, name)
    fun renameRequest(id: String, name: String) = requestTable.renameRequest(id, name)

    fun deleteCollection(id: String) = collectionTable.deleteCollection(id)
    fun deleteFolder(id: String) = folderTable.deleteFolder(id)
    fun deleteRequest(id: String) = requestTable.deleteRequest(id)

    fun countFolderContents(folderId: String) = folderTable.countFolderContents(folderId)
    fun listCollectionIds() = collectionTable.listCollectionIds()
    fun collectionExists(collectionId: String) = collectionTable.collectionExists(collectionId)

    fun getCollectionAuth(collectionId: String) = collectionTable.getCollectionAuth(collectionId)
    fun updateCollectionAuth(collectionId: String, auth: tree.PostmanAuth?) = collectionTable.updateCollectionAuth(collectionId, auth)
    fun getCollectionOpenApiSource(collectionId: String) = collectionTable.getCollectionOpenApiSource(collectionId)
    fun updateCollectionOpenApiSource(collectionId: String, sourceUrl: String?) = collectionTable.updateCollectionOpenApiSource(collectionId, sourceUrl)
    fun getFolderAuth(folderId: String) = folderTable.getFolderAuth(folderId)
    fun updateFolderAuth(folderId: String, auth: tree.PostmanAuth?) = folderTable.updateFolderAuth(folderId, auth)
    fun resolveEffectiveAuth(requestId: String) = requestTable.resolveEffectiveAuth(requestId, folderTable, collectionTable)

    fun getFolderCollectionId(folderId: String) = folderTable.getFolderCollectionId(folderId)

    // ── Cross-table coordination ────────────────────────────

    fun newFolderTarget(selection: TreeSelection?): Pair<String, String?>? = synchronized(lock) {
        if (selection == null) return@synchronized null
        when (selection) {
            is TreeSelection.Collection -> selection.id to null
            is TreeSelection.Folder -> {
                val cid = folderTable.getFolderCollectionId(selection.id) ?: return@synchronized null
                cid to selection.id
            }
            is TreeSelection.Request -> {
                val (cid, reqFolderId) = requestTable.requestCollectionAndFolder(selection.id) ?: return@synchronized null
                if (reqFolderId == null) cid to null
                else {
                    val parentOfReqFolder = folderTable.getFolderParentFolderId(reqFolderId)
                    cid to parentOfReqFolder
                }
            }
        }
    }

    fun newRequestTarget(selection: TreeSelection?): Pair<String, String?>? = synchronized(lock) {
        if (selection == null) return@synchronized null
        when (selection) {
            is TreeSelection.Collection -> selection.id to null
            is TreeSelection.Folder -> {
                val cid = folderTable.getFolderCollectionId(selection.id) ?: return@synchronized null
                cid to selection.id
            }
            is TreeSelection.Request -> requestTable.requestCollectionAndFolder(selection.id)
        }
    }

    fun applyTreeDrop(payload: TreeDragPayload, target: TreeDropTarget): Boolean = synchronized(lock) {
        when (payload) {
            is TreeDragPayload.Folder -> when (target) {
                is TreeDropTarget.FolderSlot ->
                    folderTable.moveFolder(payload.id, target.parentFolderId, target.insertIndex, target.collectionId)
                is TreeDropTarget.IntoFolder -> {
                    val n = folderTable.loadOrderedFolderIds(target.collectionId, target.folderId).size
                    folderTable.moveFolder(payload.id, target.folderId, n, target.collectionId)
                }
                is TreeDropTarget.IntoCollection ->
                    folderTable.moveFolder(payload.id, null, 0, target.collectionId)
                is TreeDropTarget.RequestSlot -> false
            }
            is TreeDragPayload.Request -> when (target) {
                is TreeDropTarget.RequestSlot ->
                    requestTable.moveRequest(payload.id, target.folderId, target.insertIndex, target.collectionId)
                is TreeDropTarget.IntoFolder -> {
                    val n = requestTable.loadOrderedRequestIds(target.collectionId, target.folderId).size
                    requestTable.moveRequest(payload.id, target.folderId, n, target.collectionId)
                }
                is TreeDropTarget.IntoCollection ->
                    requestTable.moveRequest(payload.id, null, 0, target.collectionId)
                is TreeDropTarget.FolderSlot -> false
            }
        }
    }

    // ── Export / Import ─────────────────────────────────────

    fun exportPortableCollection(collectionId: String): PortableCollection? = synchronized(lock) {
        val row = collectionTable.getCollectionRow(collectionId) ?: return@synchronized null
        PortableCollection(
            name = row.name,
            collectionMetaJson = row.metaJson,
            auth = extractAuthFromMetaJson(row.metaJson),
            folders = folderTable.buildPortableFolderTree(collectionId, null),
            rootRequests = requestTable.loadPortableRequests(collectionId, null),
            id = collectionId,
        )
    }

    fun mergeOpenApiIntoCollection(targetCollectionId: String, portable: PortableCollection) = synchronized(lock) {
        val existing = exportPortableCollection(targetCollectionId)
        val preserved = if (existing == null) portable else portable.copy(
            folders = preserveOpenApiFolders(portable.folders, buildOpenApiRequestIndex(existing)),
            rootRequests = preserveOpenApiRequests(portable.rootRequests, buildOpenApiRequestIndex(existing)),
        )
        mergePortableIntoCollection(targetCollectionId, preserved)
    }

    private data class OpenApiRequestSnapshot(
        val request: PortableRequest,
        val keys: Set<String>,
    )

    private fun buildOpenApiRequestIndex(collection: PortableCollection): Map<String, OpenApiRequestSnapshot> {
        val snapshots = mutableListOf<OpenApiRequestSnapshot>()
        fun visitRequests(requests: List<PortableRequest>) {
            requests.forEach { request ->
                val keys = openApiRequestKeys(request).toMutableSet()
                request.id?.takeIf { it.isNotBlank() }?.let { keys += "id:$it" }
                if (keys.isNotEmpty()) snapshots += OpenApiRequestSnapshot(request, keys)
            }
        }
        fun visitFolders(folders: List<PortableFolder>) {
            folders.forEach { folder ->
                visitRequests(folder.requests)
                visitFolders(folder.folders)
            }
        }
        visitRequests(collection.rootRequests)
        visitFolders(collection.folders)
        return buildMap {
            snapshots.forEach { snapshot ->
                snapshot.keys.forEach { key -> putIfAbsent(key, snapshot) }
            }
        }
    }

    private fun preserveOpenApiFolders(
        folders: List<PortableFolder>,
        existingByKey: Map<String, OpenApiRequestSnapshot>,
    ): List<PortableFolder> {
        return folders.map { folder ->
            folder.copy(
                folders = preserveOpenApiFolders(folder.folders, existingByKey),
                requests = preserveOpenApiRequests(folder.requests, existingByKey),
            )
        }
    }

    private fun preserveOpenApiRequests(
        requests: List<PortableRequest>,
        existingByKey: Map<String, OpenApiRequestSnapshot>,
    ): List<PortableRequest> {
        return requests.map { incoming ->
            val existing = openApiRequestKeys(incoming).asSequence()
                .mapNotNull { existingByKey[it] }
                .firstOrNull()
                ?: incoming.id?.let { existingByKey["id:$it"] }
            if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    id = existing.request.id,
                    url = existing.request.url,
                    headersText = existing.request.headersText,
                    paramsText = existing.request.paramsText,
                    bodyText = existing.request.bodyText,
                    auth = existing.request.auth,
                )
            }
        }
    }

    private fun openApiRequestKeys(request: PortableRequest): Set<String> {
        val meta = parseOpenApiMeta(request.metaJson) ?: return emptySet()
        val keys = mutableSetOf<String>()
        meta.syncKey?.let { keys += "sync:$it" }
        if (!meta.method.isNullOrBlank() && !meta.path.isNullOrBlank()) {
            keys += "sync:${openApiSyncKey(meta.method, meta.path)}"
            keys += "raw:${meta.method.uppercase()} ${meta.path}"
        }
        meta.operationId?.let { keys += "operation:$it" }
        return keys
    }

    private data class OpenApiRequestMeta(
        val syncKey: String?,
        val method: String?,
        val path: String?,
        val operationId: String?,
    )

    private fun parseOpenApiMeta(metaJson: String): OpenApiRequestMeta? {
        return try {
            val root = repositoryMetaJson.decodeFromString<JsonObject>(metaJson.ifBlank { "{}" })
            val openApi = root["openapi"]?.jsonObject ?: return null
            OpenApiRequestMeta(
                syncKey = openApi["syncKey"]?.jsonPrimitive?.contentOrNull,
                method = openApi["method"]?.jsonPrimitive?.contentOrNull,
                path = openApi["path"]?.jsonPrimitive?.contentOrNull,
                operationId = openApi["operationId"]?.jsonPrimitive?.contentOrNull,
            )
        } catch (e: Exception) {
            null
        }
    }
    fun mergePortableIntoCollection(targetCollectionId: String, portable: PortableCollection) = synchronized(lock) {
        if (!collectionTable.collectionExists(targetCollectionId)) return@synchronized
        val now = System.currentTimeMillis()
        val oldAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            val metaJson = mergeAuthIntoMetaJson(portable.collectionMetaJson, portable.auth)
            conn.prepareStatement(
                "UPDATE collections SET name = ?, meta_json = ?, updated_at = ? WHERE id = ?"
            ).use { ps ->
                ps.setString(1, portable.name)
                ps.setString(2, metaJson)
                ps.setLong(3, now)
                ps.setString(4, targetCollectionId)
                ps.executeUpdate()
            }
            folderTable.mergePortableFolders(targetCollectionId, null, portable.folders, now, requestTable)
            requestTable.mergePortableRequestsInFolder(targetCollectionId, null, portable.rootRequests, now)
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    fun importAsNewCollectionWithFixedId(collectionId: String, portable: PortableCollection) = synchronized(lock) {
        require(collectionId.isNotBlank())
        if (collectionTable.collectionExists(collectionId)) {
            throw IllegalStateException("集合已存在: $collectionId")
        }
        val now = System.currentTimeMillis()
        val name = portable.name
        val metaJson = mergeAuthIntoMetaJson(portable.collectionMetaJson, portable.auth)
        val oldAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            val sort = collectionTable.nextCollectionSortOrder()
            conn.prepareStatement(
                """
                INSERT INTO collections (id, name, sort_order, created_at, updated_at, meta_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, collectionId)
                ps.setString(2, name)
                ps.setInt(3, sort)
                ps.setLong(4, now)
                ps.setLong(5, now)
                ps.setString(6, metaJson)
                ps.executeUpdate()
            }
            folderTable.insertPortableFolders(collectionId, null, portable.folders, now, requestTable)
            for (req in portable.rootRequests) {
                requestTable.insertPortableRequest(collectionId, null, req, now)
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = oldAutoCommit
        }
    }

    fun importAsNewCollection(portable: PortableCollection, collectionName: String? = null): String = synchronized(lock) {
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
                ps.setInt(3, collectionTable.nextCollectionSortOrder())
                ps.setLong(4, now)
                ps.setLong(5, now)
                ps.setString(6, metaJson)
                ps.executeUpdate()
            }
            folderTable.insertPortableFolders(collId, null, portable.folders, now, requestTable)
            for (req in portable.rootRequests) {
                requestTable.insertPortableRequest(collId, null, req, now)
            }
            conn.commit()
            collId
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    // ── Internal ────────────────────────────────────────────

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
}
