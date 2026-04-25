# Postman 数据格式兼容

本文介绍 Postman Collection 格式导入导出与数据同步。

## 15.1 Postman Collection v2.1 格式

### JSON 结构

```json
{
  "info": {
    "name": "My Collection",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Folder 1",
      "item": [
        {
          "name": "GET Request",
          "request": {
            "method": "GET",
            "url": "https://api.example.com/data",
            "header": []
          }
        }
      ]
    }
  ]
}
```

### 版本兼容性

| 版本 | 说明 |
|------|------|
| v2.0 | 旧版 Postman 格式 |
| v2.1 | 当前主流格式 |

## 15.2 导入实现

### 解析入口

```kotlin
fun parsePostmanCollectionJsonToPortable(jsonText: String): PortableCollection {
    val root = importJson.parseToJsonElement(jsonText).jsonObject
    
    val info = root["info"]?.jsonObject
        ?: throw IllegalArgumentException("缺少 info")
    val name = info["name"]?.jsonPrimitive?.contentOrNull?.trim()
    
    val auth = root["auth"]?.let { parseAuthOrNull(it) }
    val items = root["item"]?.jsonArray ?: JsonArray(emptyList())
    val (folders, rootRequests) = parseItemList(items)
    
    return PortableCollection(name = name, folders = folders, rootRequests = rootRequests, auth = auth)
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
    if (schema.isNotEmpty()) {
        val s = schema.lowercase()
        if ("openapi" in s || "swagger" in s) {
            throw IllegalArgumentException("请选择 Postman Collection JSON（当前文件像是 OpenAPI）")
        }
    }
    // ...
}
```

### 递归解析 items

```kotlin
private fun parseItemList(items: JsonArray): Pair<List<PortableFolder>, List<PortableRequest>> {
    val folders = mutableListOf<PortableFolder>()
    val requests = mutableListOf<PortableRequest>()
    items.forEachIndexed { index, el ->
        val obj = el.jsonObject
        if (obj.containsKey("request")) {
            requests += parseRequestItem(obj, index)
        } else {
            folders += parseFolderItem(obj, index)
        }
    }
    return folders to requests
}
```

## 15.3 导出实现

### 构建 Postman JSON

```kotlin
fun portableCollectionToPostmanV21Json(portable: PortableCollection): String {
    val root = buildPostmanCollectionRoot(portable)
    return jsonPretty.encodeToString(JsonElement.serializer(), root)
}

private fun buildPostmanCollectionRoot(portable: PortableCollection): JsonObject {
    val info = buildJsonObject {
        put("name", portable.name)
        put("schema", POSTMAN_COLLECTION_SCHEMA_V21)
        put("_postman_id", UUID.randomUUID().toString())
    }
    return buildJsonObject {
        put("info", info)
        portable.auth?.let { put("auth", authToPostmanJson(it)) }
        put("item", buildJsonArray {
            mergePortableRootItems(portable).forEach { add(it) }
        })
    }
}
```

> 项目中的实现 (`src/main/kotlin/tree/PostmanCollectionV21Export.kt:36-61`):
```kotlin
fun portableCollectionToPostmanV21Json(portable: PortableCollection): String {
    val root = buildPostmanCollectionRoot(portable)
    return jsonPretty.encodeToString(JsonElement.serializer(), root)
}

private fun buildPostmanCollectionRoot(portable: PortableCollection): JsonObject {
    val meta = parseMetaObject(portable.collectionMetaJson)
    val info = buildJsonObject {
        put("name", portable.name)
        put("schema", POSTMAN_COLLECTION_SCHEMA_V21)
        val id = meta.stringOrNull("_postman_id") ?: UUID.randomUUID().toString()
        put("_postman_id", id)
        portable.id?.takeIf { it.isNotBlank() }?.let { put("_api_x_id", it) }
    }
    return buildJsonObject {
        put("info", info)
        authToPostmanJson(portable.auth)?.let { put("auth", it) }
        put("item", buildJsonArray {
            mergePortableRootItems(portable).forEach { add(it) }
        })
    }
}
```

### URL 与参数处理

```kotlin
private fun urlToPostmanObject(urlRaw: String, paramsText: String): JsonObject {
    val merged = mergeUrlWithParams(urlRaw.trim(), parseHeadersForSend(paramsText))
    val queryArr = buildJsonArray {
        for (line in queryBlockText.lines()) {
            parseHeaderLine(l)?.let { (k, v) ->
                addJsonObject {
                    put("key", k)
                    put("value", v)
                    put("disabled", false)
                }
            }
        }
    }
    return buildJsonObject {
        put("raw", merged)
        if (queryArr.isNotEmpty()) put("query", queryArr)
    }
}
```

## 15.4 数据目录同步机制

### Push 导出

```kotlin
object DataDirSync {
    fun pushToDataDir(repository: CollectionRepository): DataPushResult {
        val collDir = AppPaths.gitDataCollectionDir()
        val envFile = AppPaths.gitDataEnvironmentsFile()
        
        var n = 0
        for (id in repository.listCollectionIds()) {
            val p = repository.exportPortableCollection(id) ?: continue
            val json = portableCollectionToPostmanV21Json(p)
            val file = collDir.resolve("$id.json")
            Files.writeString(file, json)
            n++
        }
        
        // 环境导出
        EnvironmentStore.writeSnapshotToPath(envFile)
        
        return DataPushResult(n, true)
    }
}
```

> 项目中的实现 (`src/main/kotlin/app/DataDirSync.kt:39-66`):
```kotlin
fun pushToDataDir(repository: CollectionRepository): DataPushResult {
    return try {
        val collDir = AppPaths.gitDataCollectionDir()
        val envFile = AppPaths.gitDataEnvironmentsFile()
        var n = 0
        for (id in repository.listCollectionIds()) {
            val p = repository.exportPortableCollection(id) ?: continue
            val json = portableCollectionToPostmanV21Json(p)
            val file = collDir.resolve("$id.json")
            Files.writeString(file, json, StandardCharsets.UTF_8)
            n++
        }
        // 环境写入
        EnvironmentStore.writeSnapshotToPath(envFile)
        DataPushResult(n, true, null)
    } catch (e: Exception) {
        DataPushResult(0, false, e.message ?: e.toString())
    }
}
```

### Pull 导入（合并）

```kotlin
fun pullFromDataDir(repository: CollectionRepository, data: Path): DataPullResult {
    var merged = 0
    var created = 0
    
    val collDir = data.resolve("collection")
    Files.list(collDir).forEach { path ->
        val text = Files.readString(path)
        val portable = parsePostmanCollectionJsonToPortable(text)
        val idFromName = path.fileName.toString().removeSuffix(".json")
        
        if (repository.collectionExists(idFromName)) {
            repository.mergePortableIntoCollection(idFromName, portable)
            merged++
        } else {
            repository.importAsNewCollectionWithFixedId(idFromName, portable)
            created++
        }
    }
    
    return DataPullResult(merged, created, true)
}
```

> 项目中的实现 (`src/main/kotlin/app/DataDirSync.kt:72-100`):
```kotlin
fun pullFromDataDir(repository: CollectionRepository, data: Path = AppPaths.gitDataRoot()): DataPullResult {
    return try {
        var merged = 0
        var created = 0
        val fileErr = mutableListOf<String>()
        val collDir = data.resolve("collection")
        if (Files.isDirectory(collDir)) {
            Files.list(collDir).use { stream ->
                stream.filter { f ->
                    f.fileName.toString().endsWith(".json", ignoreCase = true)
                }.forEach { path ->
                    runCatching {
                        val text = Files.readString(path, StandardCharsets.UTF_8)
                        val portable = parsePostmanCollectionJsonToPortable(text)
                        val idFromName = path.fileName.toString().removeSuffix(".json")
                        if (repository.collectionExists(idFromName)) {
                            repository.mergePortableIntoCollection(idFromName, portable)
                            merged++
                        } else {
                            repository.importAsNewCollectionWithFixedId(idFromName, portable)
                            created++
                        }
                    }.onFailure { e ->
                        fileErr += "${path.fileName}: ${e.message}"
                    }
                }
            }
        }
        DataPullResult(merged, created, true, fileErr)
    } catch (e: Exception) {
        DataPullResult(0, 0, false, error = e.message)
    }
}
```

## 15.5 Git 版本化管理数据

### 目录结构

```
data/
├── collection/           # 集合 JSON 文件
│   ├── xxx-uuid-1.json
│   └── xxx-uuid-2.json
└── env/
    └── environments.json  # 环境配置
```

### 优势

1. **版本追踪** - 每次修改都有记录
2. **团队协作** - 共享数据变更
3. **回滚方便** - 任意版本恢复
4. **冲突解决** - 手动合并 JSON

## 15.6 总结

| 功能 | 实现 |
|------|------|
| 导入 | `parsePostmanCollectionJsonToPortable` |
| 导出 | `portableCollectionToPostmanV21Json` |
| Push | 遍历集合导出为 JSON |
| Pull | 合并不删除策略 |
| Git 管理 | data 目录独立版本控制 |

**下篇**：JDK 21 调试与性能监控