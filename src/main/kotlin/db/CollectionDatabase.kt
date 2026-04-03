package db

import java.sql.Connection
import java.sql.Statement

object CollectionDatabase {

    private const val CURRENT_VERSION = 1

    fun migrate(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY NOT NULL
                )
                """.trimIndent()
            )
        }
        val applied = conn.createStatement().use { st ->
            st.executeQuery("SELECT version FROM schema_migrations ORDER BY version").use { rs ->
                buildSet {
                    while (rs.next()) add(rs.getInt(1))
                }
            }
        }
        if (!applied.contains(1)) {
            conn.createStatement().use { st -> migrateToV1(st) }
            conn.prepareStatement("INSERT INTO schema_migrations(version) VALUES (1)").use { it.executeUpdate() }
        }
    }

    private fun migrateToV1(st: Statement) {
        st.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS collections (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                meta_json TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent()
        )
        st.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS folders (
                id TEXT PRIMARY KEY NOT NULL,
                collection_id TEXT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
                parent_folder_id TEXT REFERENCES folders(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                meta_json TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent()
        )
        st.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS requests (
                id TEXT PRIMARY KEY NOT NULL,
                collection_id TEXT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
                folder_id TEXT REFERENCES folders(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                method TEXT NOT NULL,
                url TEXT NOT NULL,
                headers_text TEXT NOT NULL DEFAULT '',
                body_text TEXT NOT NULL DEFAULT '',
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                meta_json TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent()
        )
        st.executeUpdate(
            "CREATE INDEX IF NOT EXISTS idx_folders_collection ON folders(collection_id)"
        )
        st.executeUpdate(
            "CREATE INDEX IF NOT EXISTS idx_folders_parent ON folders(parent_folder_id)"
        )
        st.executeUpdate(
            "CREATE INDEX IF NOT EXISTS idx_requests_collection ON requests(collection_id)"
        )
        st.executeUpdate(
            "CREATE INDEX IF NOT EXISTS idx_requests_folder ON requests(folder_id)"
        )
    }

    fun currentSchemaVersion(): Int = CURRENT_VERSION
}
