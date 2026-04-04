import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.github.kuangcp"
version = "1.0-SNAPSHOT"

val appIconPng = layout.projectDirectory.file("api.png").asFile

tasks.processResources {
    if (appIconPng.exists()) {
        from(appIconPng) {
            rename { "app-icon.png" }
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

compose.desktop {
    application {
        mainClass = "app.MainKt"
        jvmArgs += listOf(
            "-Xms256m",
            "-Xmx700m",
            "-Xss512k",
            "-XX:MaxDirectMemorySize=512M",
            "-XX:+UseG1GC"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "api-x"
            packageVersion = "1.0.0"
            // java.sql：SQLite JDBC 需要 DriverManager 等（jlink 默认运行时未包含）
            modules("java.net.http", "java.sql")
            // 根目录 api-3.png；Linux 打包用 PNG 最合适。Windows 安装包若失败可另备 .ico，macOS 可另备 .icns。
            if (appIconPng.exists()) {
                linux {
                    iconFile.set(appIconPng)
                }
                windows {
                    iconFile.set(appIconPng)
                }
                macOS {
                    iconFile.set(appIconPng)
                }
            }
        }
    }
}
