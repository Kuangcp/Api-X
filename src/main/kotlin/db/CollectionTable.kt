package db

import java.sql.Connection
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
}
