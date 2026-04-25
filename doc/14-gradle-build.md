# Gradle Kotlin DSL 与打包配置

本文介绍 build.gradle.kts 配置与桌面应用打包。

## 14.1 项目配置基础

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.github.kuangcp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
```

> 项目中的配置 (`build.gradle.kts:1-39`):
```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.github.kuangcp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
```

## 14.2 Compose Desktop 配置

### application 配置块

```kotlin
compose.desktop {
    application {
        mainClass = "app.MainKt"
        
        jvmArgs += listOf(
            "-Xms256m",
            "-Xmx700m",
            "-Xss512k",
        )
    }
}
```

### JVM 参数调优

```kotlin
jvmArgs += listOf(
    "-Xms256m",           // 初始堆内存
    "-Xmx700m",           // 最大堆内存
    "-Xss512k",           // 线程栈大小
    "-XX:MaxDirectMemorySize=512M",  // 直接内存
    "-XX:+UseG1GC",       // G1 垃圾收集器
    "-XX:NativeMemoryTracking=detail",  // 内存追踪
    "-Dskiko.renderApi=OPENGL",  // Skiko 渲染
)
```

> 项目中的配置 (`build.gradle.kts:44-53`):
```kotlin
compose.desktop {
    application {
        mainClass = "app.MainKt"
        jvmArgs += listOf(
            "-Xms256m",
            "-Xmx700m",
            "-Xss512k",
            "-XX:MaxDirectMemorySize=512M",
            "-XX:+UseG1GC",
            "-XX:NativeMemoryTracking=detail",
            "-Dskiko.renderApi=OPENGL",
        )
    }
}
```

## 14.3 资源处理

### 应用图标

```kotlin
val appIconPng = layout.projectDirectory.file("api.png").asFile

tasks.processResources {
    if (appIconPng.exists()) {
        from(appIconPng) {
            rename { "app-icon.png" }
        }
    }
}
```

> 项目中的资源处理 (`build.gradle.kts:13-21`):
```kotlin
val appIconPng = layout.projectDirectory.file("api.png").asFile

tasks.processResources {
    if (appIconPng.exists()) {
        from(appIconPng) {
            rename { "app-icon.png" }
        }
    }
}
```

## 14.4 打包配置

### nativeDistributions

```kotlin
nativeDistributions {
    targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
    packageName = "api-x"
    packageVersion = "1.0.0"
    
    // Java 模块（SQLite 需要）
    modules("java.net.http", "java.sql")
    
    // 图标
    if (appIconPng.exists()) {
        linux { iconFile.set(appIconPng) }
        windows { iconFile.set(appIconPng) }
        macOS { iconFile.set(appIconPng) }
    }
}
```

> 项目中的打包配置 (`build.gradle.kts:55-73`):
```kotlin
nativeDistributions {
    targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
    packageName = "api-x"
    packageVersion = "1.0.0"
    modules("java.net.http", "java.sql")
    if (appIconPng.exists()) {
        linux { iconFile.set(appIconPng) }
        windows { iconFile.set(appIconPng) }
        macOS { iconFile.set(appIconPng) }
    }
}
```

### 平台说明

| 平台 | 安装包格式 |
|------|---------|
| Windows | `.msi` |
| macOS | `.dmg` |
| Linux | `.deb` |

## 14.5 Gradle 任务

### 常用任务

```bash
# 运行
gradle run

# 打包
gradle createDistributable

# 仅 Windows
gradle createWindowsDistributable

# 仅 macOS
gradle createMacAppDmg
```

> 项目中的运行方式 (`Readme-CN.md`):
```markdown
- `gradle run` 调试运行
- `gradle createDistributable` 打包
```

## 14.6 properties 配置

### gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
android.useAndroidX=true
```

> 项目中的配置 (`gradle.properties`):
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
android.useAndroidX=true
```

## 14.7 settings.gradle.kts

```kotlin
rootProject.name = "api-x"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

> 项目中的配置 (`settings.gradle.kts`):
```kotlin
rootProject.name = "api-x"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

## 14.8 总结

| 配置项 | 说明 |
|------|------|
| `mainClass` | 入口类 |
| `jvmArgs` | JVM 参数 |
| `modules` | Java 模块 |
| `targetFormats` | 安装包格式 |
| `modules("java.net.http", "java.sql")` | SQLite 必需模块 |

**下篇**：Postman 数据格式兼容