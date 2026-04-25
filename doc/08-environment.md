# 环境变量系统设计

本文介绍环境变量切换、变量替换解析与 Auth 继承机制

## 8.1 环境变量概述

### 模型定义

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
) {
    fun activeEnvironment(): Environment? =
        activeEnvironmentId?.let { id -> environments.find { it.id == id } }
}
```

## 8.2 环境切换机制

### 单环境默认启用

```kotlin
fun withDefaultActiveWhenSingle(state: EnvironmentsState): EnvironmentsState {
    if (state.activeEnvironmentId != null) return state
    if (state.environments.size != 1) return state
    return state.copy(activeEnvironmentId = state.environments.first().id)
}
```

### UI 切换

```kotlin
@Composable
fun EnvironmentSelector(
    state: EnvironmentsState,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(state.activeEnvironment()?.name ?: "无环境")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("无环境") },
                onClick = { onSelect(null); expanded = false }
            )
            state.environments.forEach { env ->
                DropdownMenuItem(
                    text = { Text(env.name) },
                    onClick = { onSelect(env.id); expanded = false }
                )
            }
        }
    }
}
```

> 项目中的环境管理 (`src/main/kotlin/app/EnvironmentManagerDialog.kt`):
```kotlin
@Composable
fun EnvironmentManagerDialog(
    state: EnvironmentsState,
    onStateChange: (EnvironmentsState) -> Unit,
    onDismiss: () -> Unit,
) {
    // 环境列表编辑
    // 新增/删除环境
    // 变量编辑
}
```

## 8.3 变量替换解析器

### 替换函数

```kotlin
fun String.applyEnvironmentVariables(varMap: Map<String, String>): String {
    var result = this
    for ((key, value) in varMap) {
        result = result.replace("{{$key}}", value)
    }
    return result
}
```

> 项目中的实现 (`src/main/kotlin/http/EnvironmentSubstitute.kt`):
```kotlin
object EnvironmentSubstitute {
    private val VAR_PATTERN = Regex("""\{\{([^}]+)}}""")
    
    fun applyEnvironmentVariables(
        text: String,
        varMap: Map<String, String>,
    ): String {
        return text.replace(VAR_PATTERN) { match ->
            val key = match.groupValues[1].trim()
            varMap[key] ?: match.value
        }
    }
    
    fun substitutionMapForActive(variables: List<EnvVariable>): Map<String, String> {
        return variables.associate { it.key to it.value }
    }
}
```

### 使用处

```kotlin
val activeEnv = environmentsState.activeEnvironment()
val varMap = activeEnv?.variables?.let { substitutionMapForActive(it) } ?: emptyMap()

// URL 替换
val resolvedUrl = url.applyEnvironmentVariables(varMap)

// Header 替换
val resolvedHeaders = headersText.applyEnvironmentVariables(varMap)

// Body 替换
val resolvedBody = bodyText.applyEnvironmentVariables(varMap)
```

> 项目中的使用 (`src/main/kotlin/app/Main.kt:305-315`):
```kotlin
var environmentsState by remember {
    mutableStateOf(
        withDefaultActiveWhenSingle(EnvironmentStore.snapshot())
    )
}

fun commitEnvironmentsState(newState: EnvironmentsState) {
    EnvironmentStore.replace(newState)
    environmentsState = EnvironmentStore.snapshot()
}

val env = environmentsState.activeEnvironment()
val envVarMap = env?.variables?.let { envVarMapForActive(it) } ?: emptyMap()
```

## 8.4 Auth 继承与处理

### Auth 解析

```kotlin
fun resolveAuthToHeaders(auth: PostmanAuth?, varMap: Map<String, String>): List<Pair<String, String>> {
    if (auth == null || auth.type == "noauth") return emptyList()
    
    return when (auth.type) {
        "basic" -> {
            val username = applyEnvironmentVariables(auth.basic.findValue("username") ?: "", varMap)
            val password = applyEnvironmentVariables(auth.basic.findValue("password") ?: "", varMap)
            val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            listOf("Authorization" to "Basic $encoded")
        }
        "bearer" -> {
            val token = applyEnvironmentVariables(auth.bearer.findValue("token") ?: "", varMap)
            listOf("Authorization" to "Bearer $token")
        }
        "apikey" -> {
            val key = applyEnvironmentVariables(auth.apikey.findValue("key") ?: "", varMap)
            val value = applyEnvironmentVariables(auth.apikey.findValue("value") ?: "", varMap)
            val addTo = auth.apikey.findValue("in") ?: "header"
            if (addTo == "header") {
                listOf(key to value)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }
}
```

> 项目中的实现 (`src/main/kotlin/http/AuthResolver.kt`):
```kotlin
fun resolveAuthToHeaders(auth: PostmanAuth?, varMap: Map<String, String>): List<Pair<String, String>> {
    if (auth == null || auth.type == "noauth") return emptyList()

    return when (auth.type) {
        "basic" -> {
            val username = applyEnvironmentVariables(auth.basic.findValue("username") ?: "", varMap)
            val password = applyEnvironmentVariables(auth.basic.findValue("password") ?: "", varMap)
            val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            listOf("Authorization" to "Basic $encoded")
        }
        "bearer" -> {
            val token = applyEnvironmentVariables(auth.bearer.findValue("token") ?: "", varMap)
            listOf("Authorization" to "Bearer $token")
        }
        "apikey" -> {
            val key = applyEnvironmentVariables(auth.apikey.findValue("key") ?: "", varMap)
            val value = applyEnvironmentVariables(auth.apikey.findValue("value") ?: "", varMap)
            val addTo = auth.apikey.findValue("in") ?: "header"
            if (addTo == "header") {
                listOf(key to value)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }
}
```

### Auth 继承规则

文件夹级别的 Auth 会向下继承到子请求：

```kotlin
fun resolveAuthInherited(
    requestAuth: PostmanAuth?,
    folderAuth: PostmanAuth?,
    collectionAuth: PostmanAuth?,
): PostmanAuth? {
    return requestAuth ?: folderAuth ?: collectionAuth
}
```

## 8.5 变量存储

### 文件存储

```kotlin
object EnvironmentStore {
    private val envFile = File(configHome(), "environments.json")
    
    fun load(): EnvironmentsState {
        return if (envFile.exists()) {
            try {
                json.decodeFromString<EnvironmentsState>(envFile.readText())
            } catch (e: Exception) {
                EnvironmentsState()
            }
        } else {
            EnvironmentsState()
        }
    }
    
    fun save(state: EnvironmentsState) {
        envFile.writeText(json.encodeToString(state))
    }
}
```

> 项目中的存储 (`src/main/kotlin/app/EnvironmentStore.kt`):
```kotlin
object EnvironmentStore {
    private var cache: EnvironmentsState? = null
    
    private fun envFile(): File = File(configHome(), "environments.json")
    
    fun snapshot(): EnvironmentsState = cache ?: load().also { cache = it }
    
    fun replace(newState: EnvironmentsState) {
        cache = newState
        save(newState)
    }
    
    private fun load(): EnvironmentsState {
        return try {
            val text = envFile().readText()
            json.decodeFromString<EnvironmentsState>(text)
        } catch (e: Exception) {
            EnvironmentsState()
        }
    }
    
    private fun save(state: EnvironmentsState) {
        envFile().writeText(json.encodeToString(state))
    }
}
```

## 8.6 合并与同步

### 环境数据合并

```kotlin
fun mergeEnvironmentsStateNoDelete(base: EnvironmentsState, overlay: EnvironmentsState): EnvironmentsState {
    val byId = base.environments.associateBy { it.id }.toMutableMap()
    for (e in overlay.environments) {
        byId[e.id] = e
    }
    val baseOrder = base.environments.map { it.id }
    val onlyInOverlay = overlay.environments.map { it.id }.filter { it !in baseOrder.toSet() }
    val orderedIds = baseOrder + onlyInOverlay
    val envs = orderedIds.mapNotNull { byId[it] }
    val active = when {
        overlay.activeEnvironmentId != null && overlay.activeEnvironmentId in byId -> overlay.activeEnvironmentId
        base.activeEnvironmentId != null && base.activeEnvironmentId in byId -> base.activeEnvironmentId
        else -> base.activeEnvironmentId
    }
    return EnvironmentsState(
        version = maxOf(base.version, overlay.version),
        activeEnvironmentId = active,
        environments = envs,
    ).normalized()
}
```

> 项目中的合并 (`src/main/kotlin/app/EnvironmentModels.kt:47-66`):
```kotlin
fun mergeEnvironmentsStateNoDelete(base: EnvironmentsState, overlay: EnvironmentsState): EnvironmentsState {
    val byId = base.environments.associateBy { it.id }.toMutableMap()
    for (e in overlay.environments) {
        byId[e.id] = e
    }
    val baseOrder = base.environments.map { it.id }
    val onlyInOverlay = overlay.environments.map { it.id }.filter { it !in baseOrder.toSet() }
    val orderedIds = baseOrder + onlyInOverlay
    val envs = orderedIds.mapNotNull { byId[it] }
    val active = when {
        overlay.activeEnvironmentId != null && overlay.activeEnvironmentId in byId -> overlay.activeEnvironmentId
        base.activeEnvironmentId != null && base.activeEnvironmentId in byId -> base.activeEnvironmentId
        else -> base.activeEnvironmentId
    }
    return EnvironmentsState(
        version = maxOf(base.version, overlay.version),
        activeEnvironmentId = active,
        environments = envs,
    ).normalized()
}
```

## 8.7 总结

| 功能 | 实现要点 |
|------|---------|
| 环境切换 | `activeEnvironmentId` 状态管理 |
| 变量替换 | `{{key}}` 正则匹配替换 |
| Auth 处理 | Basic/Bearer/ApiKey 三种主流方式 |
| Auth 继承 | request → folder → collection 优先级 |
| 数据存储 | JSON 文件持久化 |
| 数据合并 | upsert 逻辑，不删除旧数据 |

第二章已完成！包含 4 篇博客：
- 博客 4：HTTP + 协程
- 博客 5：请求面板与响应展示
- 博客 6：SQLite 使用
- 博客 7：序列化与 JSON
- 博客 8：环境变量系统

需要继续生成第三章吗？