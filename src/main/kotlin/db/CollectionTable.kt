package db

import java.sql.Connection
import openapi.OpenApiRoot
import tree.PostmanAuth

internal class CollectionTable(private val conn: Connection, private val lock: Any) {

    fun createCollection(name: String): String = synchronized(lock) {
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

    fun renameCollection(id: String, name: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("UPDATE collections SET name = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, now)
            ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    fun deleteCollection(id: String) = synchronized(lock) {
        conn.prepareStatement("DELETE FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate()
        }
    }

    fun listCollectionIds(): List<String> = synchronized(lock) {
        val out = mutableListOf<String>()
        conn.prepareStatement("SELECT id FROM collections ORDER BY sort_order ASC, name ASC").use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) out += rs.getString("id")
            }
        }
        out
    }

    fun collectionExists(collectionId: String): Boolean = synchronized(lock) {
        conn.prepareStatement("SELECT 1 FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, collectionId)
            ps.executeQuery().use { it.next() }
        }
    }

    fun getCollectionAuth(collectionId: String): PostmanAuth? = synchronized(lock) {
        conn.prepareStatement("SELECT meta_json FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, collectionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) return@synchronized extractAuthFromMetaJson(rs.getString("meta_json") ?: "{}")
            }
        }
        null
    }

    fun updateCollectionAuth(collectionId: String, auth: PostmanAuth?) = synchronized(lock) {
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
    fun updateCollectionOpenApiSource(collectionId: String, sourceUrl: String?) = synchronized(lock) {
        val oldMeta = getCollectionMetaJson(collectionId)
        val newMeta = mergeOpenApiSourceIntoMetaJson(oldMeta, sourceUrl)
        conn.prepareStatement("UPDATE collections SET meta_json = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, newMeta)
            ps.setLong(2, System.currentTimeMillis())
            ps.setString(3, collectionId)
            ps.executeUpdate()
        }
    }

    fun getCollectionOpenApiSource(collectionId: String): String? = synchronized(lock) {
        extractOpenApiSourceFromMetaJson(getCollectionMetaJson(collectionId))
    }

    fun getCollectionOpenApiRoot(collectionId: String): OpenApiRoot? = synchronized(lock) {
        extractOpenApiRootFromMetaJson(getCollectionMetaJson(collectionId))
    }

    fun updateCollectionOpenApiRoot(collectionId: String, root: OpenApiRoot?) = synchronized(lock) {
        val oldMeta = getCollectionMetaJson(collectionId)
        val newMeta = mergeOpenApiRootIntoMetaJson(oldMeta, root)
        conn.prepareStatement("UPDATE collections SET meta_json = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, newMeta)
            ps.setLong(2, System.currentTimeMillis())
            ps.setString(3, collectionId)
            ps.executeUpdate()
        }
    }

    fun getCollectionColor(collectionId: String): String? = synchronized(lock) {
        extractColorFromMetaJson(getCollectionMetaJson(collectionId))
    }

    fun updateCollectionColor(collectionId: String, colorHex: String?) = synchronized(lock) {
        val oldMeta = getCollectionMetaJson(collectionId)
        val newMeta = mergeColorIntoMetaJson(oldMeta, colorHex)
        conn.prepareStatement("UPDATE collections SET meta_json = ?, updated_at = ? WHERE id = ?").use { ps ->
            ps.setString(1, newMeta)
            ps.setLong(2, System.currentTimeMillis())
            ps.setString(3, collectionId)
            ps.executeUpdate()
        }
    }

    fun getCollectionMetaJson(id: String): String = synchronized(lock) {
        conn.prepareStatement("SELECT meta_json FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getString("meta_json") ?: "{}"
            }
        }
        "{}"
    }

    data class CollectionRow(val name: String, val metaJson: String)

    fun getCollectionRow(id: String): CollectionRow? = synchronized(lock) {
        conn.prepareStatement("SELECT name, meta_json FROM collections WHERE id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return CollectionRow(rs.getString("name"), rs.getString("meta_json"))
            }
        }
    }

    fun nextCollectionSortOrder(): Int = synchronized(lock) {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM collections").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    /** 调整集合顺序：把 [id] 移动到第 [insertIndex] 个位置。 */
    fun moveCollection(id: String, insertIndex: Int): Boolean = synchronized(lock) {
        val ids = listCollectionIds().toMutableList()
        if (!ids.remove(id)) return@synchronized false
        val idx = insertIndex.coerceIn(0, ids.size)
        ids.add(idx, id)
        val now = System.currentTimeMillis()
        ids.forEachIndexed { i, cid ->
            conn.prepareStatement("UPDATE collections SET sort_order = ?, updated_at = ? WHERE id = ?").use { ps ->
                ps.setInt(1, i)
                ps.setLong(2, now)
                ps.setString(3, cid)
                ps.executeUpdate()
            }
        }
        true
    }
}
