# Kotlin 桌面应用架构设计

本文介绍 Compose 桌面应用的项目架构与设计模式。

## 13.1 模块分包策略

### 项目结构

```
src/main/kotlin/
├── app/          # 应用层 - UI 组合与状态
├── db/           # 数据层 - 数据库与存储
├── http/         # 网络层 - HTTP 请求处理
└── tree/         # 树形模型 - 数据结构定义
```

### 各层职责

- **app/** - 主界面组合、状态管理、对话框
- **db/** - SQLite 操作、Repository、数据持久化
- **http/** - HTTP 请求、响应处理、JSON 高亮
- **tree/** - 树形数据结构、导入导出

> 项目中的分包 (`src/main/kotlin/`):
```
app/    # Main.kt, SettingsWindow, GlobalSearchDialog, EnvironmentManagerDialog
db/    # CollectionRepository, CollectionDatabase, RequestResponseStore
http/  # HttpStreaming, RequestPanel, ResponsePanel, JsonSyntaxHighlight
tree/  # CollectionModels, CollectionTreeSidebar, PostmanCollectionV21Import
```

## 13.2 MVVM 模式在 Compose 中应用

### 经典 MVVM

```
View (Composable) ← ViewModel (状态) ← Model (数据)
```

### Compose 中的变体

在 Compose 中，状态本身相当于 ViewModel：

```kotlin
@Composable
fun RequestPanel(repository: Repository) {
    // 状态（ViewModel）
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // UI（View）
    Column {
        OutlinedTextField(value = url, onValueChange = { url = it })
        Button(onClick = {
            isLoading = true
            // 调用数据层
            repository.saveUrl(url)
            isLoading = false
        }) {
            Text("保存")
        }
    }
}
```

### 状态提升

```kotlin
@Composable
fun App() {
    // 提升状态到父组件
    var method by remember { mutableStateOf("GET") }
    
    // 传递给子组件
    RequestEditor(
        method = method,
        onMethodChange = { method = it }
    )
}
```

> 项目中的状态管理 (`src/main/kotlin/app/Main.kt:120-135`):
```kotlin
var method by remember { mutableStateOf("GET") }
var methodMenuExpanded by remember { mutableStateOf(false) }
var url by remember { mutableStateOf("https://httpbin.org/get") }
var headersText by remember { mutableStateOf("") }
var bodyText by remember { mutableStateOf("") }
var paramsText by remember { mutableStateOf("") }
var auth by remember { mutableStateOf<PostmanAuth?>(null) }

var tree by remember { mutableStateOf(repository.loadTree()) }
var treeSelection by remember { mutableStateOf<TreeSelection?>(null) }
```

## 13.3 Repository 数据层抽象

### 基础结构

```kotlin
class CollectionRepository(dbPath: Path) : AutoCloseable {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
    
    init {
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
        }
        CollectionDatabase.migrate(conn)
    }
    
    override fun close() {
        conn.close()
    }
}
```

### 数据操作

```kotlin
class CollectionRepository(dbPath: Path) {
    fun loadTree(): List<UiCollection>
    fun getRequest(id: String): StoredHttpRequest?
    fun createRequest(collectionId: String, folderId: String?, name: String): String
    fun saveRequestEditorFields(id: String, method: String, url: String, ...)
    fun deleteRequest(id: String)
}
```

> 项目中的 Repository (`src/main/kotlin/db/CollectionRepository.kt:65-91`):
```kotlin
fun getRequest(id: String): StoredHttpRequest? {
    conn.prepareStatement("""
        SELECT id, collection_id, folder_id, name, method, url, headers_text, params_text, body_text, meta_json
        FROM requests WHERE id = ?
    """).use { ps ->
        ps.setString(1, id)
        ps.executeQuery().use { rs ->
            if (!rs.next()) return null
            return StoredHttpRequest(
                id = rs.getString("id"),
                // ...
            )
        }
    }
}
```

## 13.4 依赖管理

### 依赖配置

```kotlin
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
```

> 项目中的依赖 (`build.gradle.kts:29-39`):
```kotlin
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
```

### 常用库

| 库 | 用途 |
|------|------|
| `kotlinx-coroutines-core` | 协程支持 |
| `kotlinx-serialization-json` | JSON 序列化 |
| `sqlite-jdbc` | SQLite 驱动 |
| `kotlinx-collections` | 集合工具 |

## 13.5 单例与对象

### object 单例

```kotlin
object AppSettingsStore {
    private var cache: AppSettings? = null
    
    fun snapshot(): AppSettings = cache ?: load().also { cache = it }
    
    private fun load(): AppSettings { /* ... */ }
    private fun save(settings: AppSettings) { /* ... */ }
}
```

### 伴生对象

```kotlin
class WindowPrefs {
    companion object {
        private fun prefsFile(): File = File(configHome(), "window.txt")
        fun load(): WindowPrefs = /* ... */
    }
}
```

> 项目中的单例 (`src/main/kotlin/app/EnvironmentStore.kt`):
```kotlin
object EnvironmentStore {
    private var cache: EnvironmentsState? = null
    
    fun snapshot(): EnvironmentsState = cache ?: load().also { cache = it }
    
    fun replace(newState: EnvironmentsState) {
        cache = newState
        save(newState)
    }
}
```

## 13.6 数据流方向

### UI → 数据 → 存储

```kotlin
// UI 调用
repository.saveRequestEditorFields(id, method, url, headers, params, body, auth)

// Repository 执行
conn.prepareStatement("UPDATE requests SET ...").use { ps ->
    ps.executeUpdate()
}

// 文件存储
object RequestResponseStore {
    fun saveLatest(requestId: String, entry: ResponseSnapshot) {
        val file = latestFile(requestId)
        file.writeText(Json.encodeToString(entry))
    }
}
```

## 13.7 接口与实现分离

### 定义数据类

```kotlin
// 便携模型 - 跨格式
data class PortableCollection(
    val name: String,
    val folders: List<PortableFolder>,
    val rootRequests: List<PortableRequest>,
)

// 存储模型 - 数据库
data class StoredRequest(
    val id: String,
    val collectionId: String,
    // ...
)

// UI 模型 - 展示
data class UiRequestSummary(
    val id: String,
    val name: String,
    val method: String,
)
```

> 项目中的模型分层 (`src/main/kotlin/tree/CollectionModels.kt`):
```kotlin
data class UiCollection(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val rootRequests: List<UiRequestSummary>,
)

data class UiFolder(
    val id: String,
    val name: String,
    val folders: List<UiFolder>,
    val requests: List<UiRequestSummary>,
)

data class UiRequestSummary(
    val id: String,
    val name: String,
    val method: String,
)
```

## 13.8 总结

| 架构要素 | 实现 |
|--------|------|
| 分层策略 | app/db/http/tree 分离 |
| MVVM | 状态即 ViewModel |
| 数据抽象 | Repository 模式 |
| 依赖管理 | Gradle 依赖 |
| 单例 | `object` 声明 |
| 模型分层 | 便携/存储/UI 模型 |

**下篇**：Gradle Kotlin DSL 与打包配置