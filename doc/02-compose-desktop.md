# Compose Desktop 初体验：构建第一个桌面应用

本文介绍 Compose Desktop 开发环境搭建与核心概念。

## 2.1 Kotlin Multiplatform 与 Compose 简介

**Kotlin Multiplatform (KMP)** 是 Kotlin 的跨平台方案：
- 同一套 Kotlin 代码可编译到 JVM、Android、iOS、Web、Native
- Compose Multiplatform 是 JetBrains 基于 Compose 开发的 UI 框架
- 支持 Desktop (Windows/macOS/Linux)、Web、Android

**Compose Desktop** 优势：
- 声明式 UI，类似 Flutter/React
- 与 Jetpack Compose 共享 API，学习成本低
- 告别 Swing/AWT 的繁琐，直接使用 Compose 生态

> 项目配置 (`build.gradle.kts`):
```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
}
```

## 2.2 最简单的 Compose Desktop 应用

```kotlin
import androidx.compose.material3.Text
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window

fun main() = application {
    Window(title = "Api-X") {
        Text("Hello Compose Desktop!")
    }
}
```

运行方式 (`src/main/kotlin/app/Main.kt:101-102`)：
```kotlin
@Composable
fun App(onExitRequest: () -> Unit) {
    // 应用主界面
}
```

在 Gradle 中配置入口点 (`build.gradle.kts:43`):
```kotlin
mainClass = "app.MainKt"
```

## 2.3 @Composable 函数

**@Composable** 是 Compose 的核心注解，标记该函数可以调用其他 Composable 或读取状态：

```kotlin
@Composable
fun MyComponent(title: String) {
    // 可调用其他 @Composable
    Column {
        Text(text = title)
        Button(onClick = { /* ... */ }) {
            Text("Click")
        }
    }
}
```

> 项目中的 Composable (`src/main/kotlin/http/RequestPanel.kt`):
```kotlin
@Composable
fun RequestTopBar(
    method: String,
    methodMenuExpanded: Boolean,
    onMethodChange: (String) -> Unit,
    // ...
) {
    // ...
    Row {
        // 方法下拉菜单
        Box {
            DropdownMenu(
                expanded = methodMenuExpanded,
                onDismissRequest = { /* ... */ }
            ) {
                // ...
            }
        }
    }
}
```

## 2.4 状态管理：remember 与 mutableStateOf

Compose 是**响应式**的：状态变化 → UI 自动更新

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    
    Button(onClick = { count++ }) {
        Text("点击次数: $count")
    }
}
```

**关键点**：
1. `mutableStateOf(0)` 创建一个可观察的状态
2. `by remember` 让状态在重组时保持（类似缓存）
3. 修改 `count++` 会自动触发 UI 重绘

> 项目中的状态用法 (`src/main/kotlin/app/Main.kt:120-130`):
```kotlin
var method by remember { mutableStateOf("GET") }
var methodMenuExpanded by remember { mutableStateOf(false) }
var url by remember { mutableStateOf("https://httpbin.org/get") }
var headersText by remember {
    mutableStateOf("Content-Type: application/x-www-form-urlencoded")
}
```

## 2.5 rememberUpdatedState 与 getValue

对于需要**在 lambda 中获取最新值**的场景：

```kotlin
// ❌ 错误：lambda 会捕获旧的 value
LaunchedEffect(Unit) {
    button.onClick {
        showDialog(userId)  // userId 可能是旧值
    }
}

// ✅ 正确：使用 rememberUpdatedState
val userId by rememberUpdatedState(userId)
LaunchedEffect(Unit) {
    button.onClick {
        showDialog(userId)  // 始终是最新值
    }
}
```

> 项目中的实际用法 (`src/main/kotlin/app/Main.kt:144-149`):
```kotlin
val editorIdSnap by rememberUpdatedState(editorRequestId)
val methodSnap by rememberUpdatedState(method)
val urlSnap by rememberUpdatedState(url)
val headersSnap by rememberUpdatedState(headersText)
val paramsSnap by rememberUpdatedState(paramsText)
val bodySnap by rememberUpdatedState(bodyText)

DisposableEffect(repository) {
    onDispose {
        // 在协程/回调中获取最新状态
        editorIdSnap?.let {
            repository.saveRequestEditorFields(it, methodSnap, urlSnap, headersSnap, paramsSnap, bodySnap, auth)
        }
    }
}
```

## 2.6 LaunchedEffect 与副作用

在 Composable 中执行副作用（网络请求、文件 IO 等）：

```kotlin
@Composable
fun MyScreen() {
    var data by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(key1) {
        // 协程作用域，自动取消
        data = fetchData(key1)
    }
    
    if (data == null) {
        CircularProgressIndicator()
    }
}
```

> 项目中的 LaunchedEffect (`src/main/kotlin/app/Main.kt:321-325`):
```kotlin
LaunchedEffect(method, url, headersText, paramsText, bodyText, auth, editorRequestId) {
    val id = editorRequestId ?: return@LaunchedEffect
    delay(450)  // 防抖
    repository.saveRequestEditorFields(id, method, url, headersText, paramsText, bodyText, auth)
}
```

## 2.7 DisposableEffect 清理资源

```kotlin
@Composable
fun MyScreen(repository: Repository) {
    DisposableEffect(repository) {
        onDispose {
            repository.close()  // 组件销毁时执行
        }
    }
}
```

> 项目中的资源清理 (`src/main/kotlin/app/Main.kt:151-166`):
```kotlin
DisposableEffect(repository) {
    onDispose {
        editorIdSnap?.let {
            repository.saveRequestEditorFields(
                it,
                methodSnap,
                urlSnap,
                headersSnap,
                paramsSnap,
                bodySnap,
                auth,
            )
        }
        repository.close()
    }
}
```

## 2.8 总结

本篇核心要点：

| 概念 | 说明 |
|------|------|
| `@Composable` | 声明式 UI 函数标记 |
| `mutableStateOf` | 可观察状态 |
| `remember` | 缓存状态值，避免重组丢失 |
| `rememberUpdatedState` | 在 lambda 中获取最新状态 |
| `LaunchedEffect` | 协程副作用 |
| `DisposableEffect` | 清理副作用 |

**下一章**：Compose 布局基础与 Material Design