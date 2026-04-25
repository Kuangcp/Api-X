# 序列化与 JSON 处理

本文介绍 kotlinx.serialization 在项目中的使用。

## 7.1 kotlinx.serialization 简介

### 依赖配置

```kotlin
plugins {
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
```

### 基本用法

```kotlin
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class User(val name: String, val age: Int)

val json = Json.encodeToString(User("Alice", 30))
// {"name":"Alice","age":30}

val user = Json.decodeFromString<User>(json)
// User(name=Alice, age=30)
```

## 7.2 data class 序列化

### 定义可序列化类

> 项目中的模型 (`src/main/kotlin/app/EnvironmentModels.kt`):
```kotlin
@Serializable
data class EnvVariable(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class Environment(
    val id: String,
    val name: String,
    val variables: List<EnvVariable> = emptyList(),
)

@Serializable
data class EnvironmentsState(
    val version: Int = 1,
    val activeEnvironmentId: String? = null,
    val environments: List<Environment> = emptyList(),
)
```

### JSON 解析与生成

```kotlin
val json = Json { ignoreUnknownKeys = true }

fun parseEnvironments(text: String): EnvironmentsState {
    return json.decodeFromString<EnvironmentsState>(text)
}

fun serializeEnvironments(state: EnvironmentsState): String {
    return json.encodeToString(state)
}
```

## 7.3 JSON 高级用法

### 配置 Json 实例

```kotlin
private val jsonLenient = Json { ignoreUnknownKeys = true }

private val jsonPretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
}
```

### 解析元素

```kotlin
val root = json.parseToJsonElement(jsonText)
// JsonObject, JsonArray, JsonPrimitive
```

> 项目中的解析 (`src/main/kotlin/http/JsonSyntaxHighlight.kt:21-28`):
```kotlin
private val jsonLenient = Json { ignoreUnknownKeys = true }
private val jsonPretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
}

fun formatJsonBodyTextOrNull(rawBody: String): String? {
    val trimmed = rawBody.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        val el = jsonLenient.parseToJsonElement(trimmed)
        jsonPretty.encodeToString(JsonElement.serializer(), el)
    }.getOrNull()
}
```

## 7.4 Postman 格式导入导出

### 导入 JSON

```kotlin
fun parsePostmanCollectionJsonToPortable(jsonText: String): PortableCollection {
    val root = importJson.parseToJsonElement(jsonText).jsonObject
    
    val info = root["info"]?.jsonObject
        ?: throw IllegalArgumentException("缺少 Postman 集合字段 info")
    
    val name = info["name"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: throw IllegalArgumentException("缺少 info.name")
    
    // 解析 items
    val items = root["item"]?.jsonArray ?: JsonArray(emptyList())
    val (folders, rootRequests) = parseItemList(items)
    
    return PortableCollection(
        name = name,
        // ...
    )
}
```

> 项目中的实现 (`src/main/kotlin/tree/PostmanCollectionV21Import.kt:30-72`):
```kotlin
fun parsePostmanCollectionJsonToPortable(jsonText: String): PortableCollection {
    val root = try {
        importJson.parseToJsonElement(jsonText).jsonObject
    } catch (e: Exception) {
        throw IllegalArgumentException("JSON 解析失败: ${e.message}")
    }
    val info = root["info"]?.jsonObject
        ?: throw IllegalArgumentException("缺少 Postman 集合字段 info")
    val name = info["name"]?.jsonPrimitive?.contentOrNull?.trim()
        ?: throw IllegalArgumentException("缺少 info.name")
    val schema = info["schema"]?.jsonPrimitive?.contentOrNull.orEmpty()
    
    // 验证不是 OpenAPI
    if (schema.isNotEmpty()) {
        val s = schema.lowercase()
        if ("openapi" in s || "swagger" in s) {
            throw IllegalArgumentException("请选择 Postman Collection JSON（当前文件像是 OpenAPI）")
        }
    }
    
    val collectionMeta = buildJsonObject {
        extractDescriptionString(info["description"])?.let { put("description", JsonPrimitive(it)) }
        info["_postman_id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("_postman_id", JsonPrimitive(it))
        }
    }
    
    val auth = root["auth"]?.let { parseAuthOrNull(it) }
    val items = root["item"]?.jsonArray ?: JsonArray(emptyList())
    val (folders, rootRequests) = parseItemList(items)
    
    return PortableCollection(
        name = name,
        collectionMetaJson = encodeJsonObjectToString(collectionMeta),
        auth = auth,
        folders = folders,
        rootRequests = rootRequests,
    )
}
```

### 导出 JSON

```kotlin
fun portableCollectionToPostmanV21Json(collection: PortableCollection): String {
    val json = jsonPretty
    
    val info = buildJsonObject {
        put("_postman_id", JsonPrimitive(collection.id ?: UUID.randomUUID().toString()))
        put("name", JsonPrimitive(collection.name))
        put("schema", JsonPrimitive("https://schema.getpostman.com/json/collection/v2.1.0/collection.json"))
    }
    
    val items = jsonArray {
        // 添加 folders 和 requests
    }
    
    val root = buildJsonObject {
        put("info", info)
        put("item", items)
    }
    
    return json.encodeToString(JsonElement.serializer(), root)
}
```

> 项目中的导出逻辑 (`src/main/kotlin/tree/PostmanCollectionV21Export.kt`):
```kotlin
fun portableCollectionToPostmanV21Json(collection: PortableCollection): String {
    val json = jsonDefault

    val info = buildJsonObject {
        put("_postman_id", JsonPrimitive(collection.id ?: UUID.randomUUID().toString()))
        put("name", JsonPrimitive(collection.name))
        put("schema", JsonPrimitive("https://schema.getpostman.com/json/collection/v2.1.0/collection.json"))
        collection.auth?.let { put("auth", encodeAuthToJson(it)) }
        put("description", JsonPrimitive("导出自 Api-X"))
    }

    val items = jsonArray {
        // 添加文件夹结构
        for (folder in collection.folders) {
            +itemJsonFromPortableFolder(folder)
        }
        // 添加根级别请求
        for (req in collection.rootRequests) {
            +itemJsonFromPortableRequest(req)
        }
    }

    return json.encodeToString(JsonElement.serializer(), buildJsonObject {
        put("info", info)
        put("item", items)
        collection.collectionMetaJson?.let { meta ->
            val obj = json.parseToJsonElement(meta).jsonObject
            obj["variable"]?.let { put("variable", it) }
        }
    })
}
```

## 7.5 处理复杂 JSON 结构

### 嵌套对象解析

```kotlin
data class PostmanAuth(
    val type: String = "no",
    val basic: List<AuthEntry>? = null,
    val bearer: List<AuthEntry>? = null,
)

fun parseAuthOrNull(el: JsonElement): PostmanAuth? = try {
    importJson.decodeFromJsonElement(PostmanAuth.serializer(), el)
} catch (_: Exception) {
    null
}
```

> 项目中的 Auth 处理 (`src/main/kotlin/tree/PostmanAuth.kt`):
```kotlin
@Serializable
data class PostmanAuth(
    val type: String = "no",
    val basic: List<AuthEntry>? = null,
    val bearer: List<AuthEntry>? = null,
    val apikey: List<AuthEntry>? = null,
)

@Serializable
data class AuthEntry(
    val key: String,
    val value: String,
    val type: String = "default",
)
```

### 构建 JSON 对象

```kotlin
fun buildJsonObject(block: MutableJsonObject.() -> Unit): JsonObject {
    val obj = JsonObject(mutableMapOf())
    obj.block()
    return obj
}

val collectionMeta = buildJsonObject {
    extractDescriptionString(info["description"])?.let { put("description", JsonPrimitive(it)) }
    info["_postman_id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
        put("_postman_id", JsonPrimitive(it))
    }
}
```

## 7.6 与数据类映射

### 内部模型 vs 便携模型

```kotlin
// 便携模型 - 用于导入导出
data class PortableCollection(
    val name: String,
    val collectionMetaJson: String?,
    val auth: PostmanAuth?,
    val folders: List<PortableFolder>,
    val requests: List<PortableRequest>,
    val id: String?,
)

// 数据库模型 - 存储用
data class StoredRequest(
    val id: String,
    val collectionId: String,
    val url: String,
    // ...
)

// UI 模型 - 展示用
data class UiRequestSummary(
    val id: String,
    val name: String,
    val method: String,
)
```

## 7.7 总结

| 功能 | API |
|------|-----|
| 序列化 | `@Serializable` + `encodeToString` |
| 反序列化 | `decodeFromString` |
| 解析元素 | `parseToJsonElement` |
| 构建对象 | `buildJsonObject` |
| 构建数组 | `jsonArray` |
| pretty print | `Json { prettyPrint = true }` |

**下篇**：环境变量系统设计