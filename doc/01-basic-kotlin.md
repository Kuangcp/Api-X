# 从 Java 到 Kotlin：语法快速上手

本文面向有 Java 基础的开发者，快速掌握 Kotlin 与 Java 的核心差异。

## 1.1 变量声明：val 与 var

Java 中我们需要声明变量的类型：
```java
String name = "Api-X";
final String APP_NAME = "Api-X";
```

Kotlin 使用 `val`（不可变）和 `var`（可变）:
```kotlin
val name = "Api-X"           // 相当于 final String
var count = 0               // 相当于 int (可修改)
var url: String = ""        // 显式指定类型
```

> 项目中的实际用法 (`src/main/kotlin/app/LastRequestPrefs.kt:10-12`):
```kotlin
companion object {
    private fun prefsFile(): File = File(configHome(), "lastRequest.txt")
    fun load(): String = prefsFile().takeIf { it.exists() }?.readText() ?: ""
}
```

## 1.2 智能类型推断

Kotlin 编译器会根据右侧表达式自动推断类型：
```kotlin
val repository = CollectionRepository(path)  // 自动推断为 CollectionRepository
val url = "https://api.example.com"             // 自动推断为 String
val expandedIds = mutableSetOf<String>()        // 自动推断为 MutableSet<String>
```

项目中的典型用法 (`src/main/kotlin/app/Main.kt:120-130`):
```kotlin
var method by remember { mutableStateOf("GET") }
var url by remember { mutableStateOf("https://httpbin.org/get") }
var headersText by remember {
    mutableStateOf("Content-Type: application/x-www-form-urlencoded")
}
```

## 1.3 数据类 data class

Java 需要手动编写 getter/setter/equals/hashCode：
```java
public class Request {
    private String id;
    private String url;
    private String method;
    // 还要手动写 getters, setters, equals, hashCode...
}
```

Kotlin 一行搞定：
```kotlin
data class Request(
    val id: String,
    val url: String,
    val method: String = "GET"
)
```

自动生成：`equals()`, `hashCode()`, `toString()`, `copy()`, `componentN()`

> 项目中的数据模型 (`src/main/kotlin/tree/CollectionModels.kt`):
```kotlin
data class Request(
    val id: String,
    val collectionId: String,
    val folderId: String?,
    val name: String,
    val url: String = "",
    val method: String = "GET",
    val headersText: String = "",
    val bodyText: String = "",
    val paramsText: String = "",
    val auth: PostmanAuth? = null,
)
```

## 1.4 解构声明

从 data class 中快速提取字段：
```kotlin
val request = Request("1", "https://api.com", "GET")
val (id, url, method) = request  // 解构
```

项目中的实际用法 (`src/main/kotlin/app/Main.kt:201-208`):
```kotlin
fun addFolderAt(at: TreeSelection) {
    val target = repository.newFolderTarget(at) ?: return
    val (cid, pid) = target  // 解构获取 collectionId 和 parentId
    repository.createFolder(cid, pid, "新文件夹").let { fid ->
        refreshTree()
        expandedCollectionIds = expandedCollectionIds + cid
        if (pid != null) expandedFolderIds = expandedFolderIds + pid
    }
}
```

## 1.5 扩展函数与属性

为现有类添加新方法，无需继承：
```kotlin
// 扩展函数
fun String.toUri(): URI = URI(this)

// 扩展属性
val String.uri: URI get() = URI(this)
```

> 项目中的实用扩展 (`src/main/kotlin/http/RequestUrl.kt`):
```kotlin
fun String.ensureDefaultHttpScheme(): String {
    if (contains("://")) return this
    return "https://$this"
}

fun String.extractUrlAndParams(): Pair<String, String> {
    val q = indexOf('?')
    return if (q > 0) {
        Pair(substring(0, q), substring(q + 1))
    } else {
        Pair(this, "")
    }
}
```

## 1.6 lambda 表达式

Java 匿名内部类 vs Kotlin lambda：

```java
// Java
button.setOnClickListener(new OnClickListener() {
    @Override
    public void onClick(View v) {
        doSomething();
    }
});
```

```kotlin
// Kotlin
button.onClick { doSomething() }

// 带参数
list.forEach { item -> println(item.name) }
```

> 项目中的 lambda 用法 (`src/main/kotlin/app/Main.kt:224-228`):
```kotlin
fun mruRequestIdsForSwitcher(): List<String> {
    val ordered = RecentRequestUsageStore.orderedIdsNewestFirst { repository.getRequest(it) != null }
    val cur = editorRequestId ?: return ordered
    val without = ordered.filter { it != cur }
    return (listOf(cur) + without).distinct().take(30)
}
```

## 1.7 空安全

Kotlin 把空指针安全作为语言特性：

```kotlin
val name: String = "Api-X"    // 非空，不能赋 null
val nameNullable: String? = null  // 可空

// 安全调用
val len = nameNullable?.length  // 如果为 null，返回 null

// Elvis 操作符
val len = nameNullable?.length ?: 0  // null 时提供默认值

// 非空断言（慎用）
val len = nameNullable!!.length  // 如果为 null 抛异常
```

> 项目中的空安全实践 (`src/main/kotlin/db/CollectionRepository.kt`):
```kotlin
fun getRequest(id: String): Request? {
    return selectRequestByIdStmt.queryForObject(id)?.let {
        it.toRequest()
    }
}

// 使用处
val r = repository.getRequest(reqId) ?: return
val savedId = LastRequestPrefs.load()
if (savedId.isNotEmpty() && repository.getRequest(savedId) != null) {
    // ...
}
```

## 1.8 高阶函数

接收或返回函数的函数：

```kotlin
// 接收函数参数
inline fun <T> lazy(t: () -> T): T = t()

// 返回函数
fun createHandler(action: () -> Unit): () -> Unit = action
```

> 项目中的高阶函数 (`src/main/kotlin/http/JsonSyntaxHighlight.kt`):
```kotlin
fun highlightJson(code: String): List<AnnotatedString> {
    // 利用正则 + lambda 处理匹配
    val patterns = listOf(
        PatternItem(Regex("\"[^\"]+\"(?=\\s*:)"), ColorKey.String),
        // ...
    )
    return buildAnnotatedString { ... }
}
```

## 1.9 object 与 companion object

单例和伴生对象：

```kotlin
// 单例（项目中的应用设置存储）
object AppSettingsStore {
    private var cache: AppSettings? = null
    fun snapshot() = cache ?: load().also { cache = it }
    // ...
}

// 伴生对象（静态成员）
class MyClass {
    companion object {
        const val VERSION = "1.0"
    }
}
```

> 项目中的实际应用 (`src/main/kotlin/app/EnvironmentStore.kt`):
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

## 总结

| Java | Kotlin |
|------|---------|
| `String s = "hi"` | `val s = "hi"` |
| `final String` | `val` |
| `String` | `var` / `val` + 类型推断 |
| getter/setter/equals | `data class` |
| 匿名内部类 | `lambda` |
| `@Nullable` | `String?` + `?.` |
| static | `companion object` / `object` |

本篇介绍了 Kotlin 最核心的语法特性，下一篇我们开始学习 Compose Desktop 环境搭建。