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
app/          # Main.kt, AppViewModel, AppActions, AppSettings, AppTheme, ui/Dialogs
  ui/         # Dialogs.kt - 统一对话框编排
db/           # CollectionRepository, CollectionDatabase, RequestResponseStore, HarExchange
http/         # HttpStreaming, AuthResolver, CurlParser, CurlExport, JsonSyntaxHighlight
  request/    # RequestTopBar, RequestSidePanel, RequestEditorPane, RequestBodyEditorTab, RequestKeyValueTabs, AuthEditor, EnvVarAutocomplete, RequestTabBar
  response/   # ResponsePanel
tree/         # CollectionModels, CollectionTreeSidebar, PostmanCollectionV21Import/Export, PortableCollection, PostmanAuth
```

## 13.2 AppViewModel 模式

项目中采用 **AppViewModel** 模式：将所有 UI 状态封装在 `AppViewModel` 数据类中，通过 `rememberAppViewModel()` Composable 函数创建。

### AppViewModel 数据类

```kotlin
data class AppViewModel(
    val repository: CollectionRepository,
    val tree: List<UiCollection>,
    val treeSelection: TreeSelection?,
    val method: String,
    val url: String,
    val headersText: String,
    val bodyText: String,
    val paramsText: String,
    val auth: PostmanAuth?,
    val editorRequestId: String?,
    val responseLines: MutableList<String>,
    val isLoading: Boolean,
    // ... 共 90+ 个字段
    val onStartRequest: () -> Unit,
    val onCancelRequest: () -> Unit,
    val onRefreshTree: () -> Unit,
)
```

### 状态创建

```kotlin
@Composable
fun App(onExitRequest: () -> Unit) {
    val vm = rememberAppViewModel(repository, expandLoaded, windowState)
    // 使用 vm 访问所有状态和方法
    MaterialTheme(colors = appMaterialColors(vm.isDarkTheme, vm.appSettings.backgroundHex)) {
        RequestTopBar(isLoading = vm.isLoading, ...)
        RequestSidePanel(props = RequestEditorProps(...))
        ResponsePanel(statusCodeText = vm.statusCodeText, ...)
    }
}
```

> 项目中的状态管理 (`src/main/kotlin/app/AppViewModel.kt:176-213`):
```kotlin
var method by remember { mutableStateOf("GET") }
var headersText by remember { mutableStateOf("Content-Type: ...") }
var bodyText by remember { mutableStateOf("key: value") }
var paramsText by remember { mutableStateOf("") }
var auth by remember { mutableStateOf<PostmanAuth?>(null) }
var tree by remember { mutableStateOf(repository.loadTree()) }
var treeSelection by remember { mutableStateOf<TreeSelection?>(null) }
var editorRequestId by remember { mutableStateOf<String?>(null) }
```

### TabSession 与 RequestSession

多标签通过 `TabSession` 管理编辑区状态，`RequestSession` 管理响应状态：

```kotlin
data class TabSession(
    val method: String = "GET",
    val url: String = "",
    val headersText: String = "",
    val bodyText: String = "",
    val paramsText: String = "",
    val auth: PostmanAuth? = null,
    val leftTabIndex: Int = 0,
)

data class RequestSession(
    val responseLines: MutableList<String>,
    val statusCodeText: String = "",
    var isLoading: Boolean = false,
    val control: RequestControl? = null,
)
```

切换标签时保存当前 TabSession，加载目标标签的 TabSession 恢复编辑区。每个请求有独立的 RequestSession，支持并发请求互不干扰。

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
    implementation("org.jetbrains.compose.material:material-icons-extended")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.neoutils.highlight:highlight-compose:2.3.0")
}
```

> 版本号统一在 `gradle.properties` 管理：`kotlin.version=2.3.20`、`compose.version=1.10.3`

### 常用库

| 库 | 用途 |
|------|------|
| `kotlinx-coroutines-core` | 协程支持 |
| `kotlinx-serialization-json` | JSON 序列化 |
| `sqlite-jdbc` | SQLite 驱动 |
| `highlight-compose` | JSON 编辑器语法高亮 |
| `compose.material-icons-extended` | Material Design 图标扩展 |

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

### UI → Model → 存储

```kotlin
// 1) 用户在 UI 编辑 → 自动保存（450ms 防抖）
LaunchedEffect(method, url, headersText, paramsText, bodyText, auth, editorRequestId) {
    delay(450)
    repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)
}

// 2) 发送请求 → 保存 HAR 快照
fun startRequest() {
    sendRequestStreaming(method, url, body, headersText, control, ...)
    // 完成后
    RequestResponseStore.save(boundRequestId, HarSnapshot(
        requestMethod = method, requestUrl = url,
        responseBodyLines = control.snapshotRawBodyLines(),
        responseTimeMs = elapsed, responseSizeBytes = control.totalBytes,
        // ...
    ))
}

// 3) 拖拽/导入导出 → Repository 事务
fun applyTreeDrop(payload, target) {
    repository.moveRequest(payload.id, target)
    refreshTree()
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
data class UiCollection(...)
data class UiFolder(...)
data class UiRequestSummary(...)

// 全局搜索专用模型
data class GlobalSearchRequestRow(
    val id: String,
    val name: String,
    val url: String,
    val headersText: String,
    val bodyText: String,
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