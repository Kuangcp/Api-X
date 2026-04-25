# SQLite 在 Kotlin 中的使用

本文介绍在 Kotlin 中使用 SQLite 进行数据持久化。

## 6.1 JDBC 连接 SQLite

### 依赖配置

```kotlin
dependencies {
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
}
```

### 连接数据库

```kotlin
class CollectionRepository(dbPath: Path) : AutoCloseable {
    private val conn: Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
    
    override fun close() {
        conn.close()
    }
}
```

> 项目中的实现 (`src/main/kotlin/db/CollectionRepository.kt:26-42`):
```kotlin
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
}
```

## 6.2 Schema 迁移

### 创建表

```kotlin
st.executeUpdate("""
    CREATE TABLE IF NOT EXISTS collections (
        id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        meta_json TEXT NOT NULL DEFAULT '{}'
    )
""".trimIndent())
```

> 项目中的完整 Schema (`src/main/kotlin/db/CollectionDatabase.kt:45-102`):
```kotlin
private fun migrateToV1(st: Statement) {
    st.executeUpdate("""
        CREATE TABLE IF NOT EXISTS collections (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            meta_json TEXT NOT NULL DEFAULT '{}'
        )
    """.trimIndent())
    st.executeUpdate("""
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
    """.trimIndent())
    st.executeUpdate("""
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
    """.trimIndent())
    // 索引
    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_folders_collection ON folders(collection_id)")
    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_folders_parent ON folders(parent_folder_id)")
    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_requests_collection ON requests(collection_id)")
    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_requests_folder ON requests(folder_id)")
}
```

### 迁移系统

```kotlin
object CollectionDatabase {
    fun migrate(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY NOT NULL
                )
            """.trimIndent())
        }
        
        val applied = conn.createStatement().use { st ->
            st.executeQuery("SELECT version FROM schema_migrations ORDER BY version").use { rs ->
                buildSet { while (rs.next()) add(rs.getInt(1)) }
            }
        }
        
        if (!applied.contains(1)) {
            conn.createStatement().use { st -> migrateToV1(st) }
            conn.prepareStatement("INSERT INTO schema_migrations(version) VALUES (1)").use { it.executeUpdate() }
        }
        if (!applied.contains(2)) {
            conn.createStatement().use { st -> migrateToV2(st) }
            conn.prepareStatement("INSERT INTO schema_migrations(version) VALUES (2)").use { it.executeUpdate() }
        }
    }
}
```

## 6.3 SELECT 操作

### 查询单条

```kotlin
fun getRequest(id: String): StoredHttpRequest? {
    conn.prepareStatement("""
        SELECT id, collection_id, folder_id, name, method, url, headers_text, body_text
        FROM requests WHERE id = ?
    """).use { ps ->
        ps.setString(1, id)
        ps.executeQuery().use { rs ->
            if (!rs.next()) return null
            return StoredHttpRequest(
                id = rs.getString("id"),
                collectionId = rs.getString("collection_id"),
                // ...
            )
        }
    }
}
```

> 项目中的查询 (`src/main/kotlin/db/CollectionRepository.kt:65-91`):
```kotlin
fun getRequest(id: String): StoredHttpRequest? {
    conn.prepareStatement("""
        SELECT id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, meta_json
        FROM requests WHERE id = ?
    """).use { ps ->
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
```

### 查询列表

```kotlin
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
        UiCollection(id = id, name = name, folders = buildFolderTree(id, null), rootRequests = loadRequestSummariesInFolder(id, null))
    }
}
```

## 6.4 INSERT/UPDATE/DELETE

### 插入

```kotlin
fun createRequest(collectionId: String, folderId: String?, name: String): String {
    val id = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    
    conn.prepareStatement("""
        INSERT INTO requests (id, collection_id, folder_id, name, method, url, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'GET', '', ?, ?, ?)
    """).use { ps ->
        ps.setString(1, id)
        ps.setString(2, collectionId)
        ps.setString(3, folderId)
        ps.setString(4, name)
        ps.setLong(5, now)
        ps.setLong(6, now)
        ps.executeUpdate()
    }
    return id
}
```

### 更新

```kotlin
fun saveRequestEditorFields(id: String, method: String, url: String, ...) {
    val now = System.currentTimeMillis()
    
    conn.prepareStatement("""
        UPDATE requests SET method = ?, url = ?, headers_text = ?, body_text = ?, updated_at = ?
        WHERE id = ?
    """).use { ps ->
        ps.setString(1, method)
        ps.setString(2, url)
        ps.setString(3, headersText)
        ps.setString(4, bodyText)
        ps.setLong(5, now)
        ps.setString(6, id)
        ps.executeUpdate()
    }
}
```

> 项目中的实现 (`src/main/kotlin/db/CollectionRepository.kt:120-149`):
```kotlin
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

    conn.prepareStatement("""
        UPDATE requests SET method = ?, url = ?, headers_text = ?, params_text = ?, body_text = ?, meta_json = ?, updated_at = ?
        WHERE id = ?
    """).use { ps ->
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
```

### 删除

```kotlin
fun deleteRequest(id: String) {
    conn.prepareStatement("DELETE FROM requests WHERE id = ?").use { ps ->
        ps.setString(1, id)
        ps.executeUpdate()
    }
}
```

## 6.5 协程中的数据库操作

### 使用 use 自动关闭

```kotlin
conn.prepareStatement(sql).use { ps ->
    ps.executeQuery().use { rs ->
        // 处理结果
    }
}  // 自动关闭
```

### 在 LaunchedEffect 中调用

```kotlin
@Composable
fun MyScreen() {
    val repository = remember { CollectionRepository(path) }
    
    DisposableEffect(repository) {
        onDispose {
            repository.close()  // 关闭连接
        }
    }
    
    LaunchedEffect(editorRequestId) {
        val request = repository.getRequest(editorRequestId)
        // 使用数据
    }
}
```

## 6.6 总结

| 操作 | 方法 |
|------|------|
| 连接 | `DriverManager.getConnection("jdbc:sqlite:...")` |
| 查询 | `PreparedStatement.executeQuery()` |
| 更新 | `PreparedStatement.executeUpdate()` |
| 事务 | `conn.setAutoCommit(false)` |
| 迁移 | `schema_migrations` 版本控制 |
| 关闭 | `use` 扩展自动关闭 |

**下篇**：序列化与 JSON 处理