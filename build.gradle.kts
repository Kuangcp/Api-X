import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.github.kuangcp"
version = "1.3.1"

val appIconPng = layout.projectDirectory.file("api.png").asFile
val appIconIco = layout.projectDirectory.file("api.ico").asFile

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
    implementation("org.jetbrains.compose.components:components-resources:${property("compose.version")}")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.neoutils.highlight:highlight-compose:2.3.0")
    implementation("org.tinylog:tinylog-api:2.7.0")
    runtimeOnly("org.tinylog:tinylog-impl:2.7.0")
}

compose.desktop {
    application {
        mainClass = "app.core.MainKt"
        jvmArgs += listOf(
            "-Xms30m",
            "-Xmx512m",
            "-Xss384k",
            "-XX:MaxDirectMemorySize=512M",
            // "-XX:+UseShenandoahGC",
            "-XX:NativeMemoryTracking=detail",
            // Skiko：OPENGL 由 GPU 合成，列表滚动更顺滑；若需对比内存/RSS 可临时改为 SOFTWARE / SOFTWARE_FAST
            "-Dskiko.renderApi=OPENGL",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "api-x"
            packageVersion = project.findProperty("version")?.toString() ?: "1.0.0"
            // java.sql：SQLite JDBC 需要 DriverManager 等（jlink 默认运行时未包含）
            modules("java.net.http", "java.sql", "java.management")
            // 根目录 api.png（Linux/macOS）、api.ico（Windows）
            if (appIconPng.exists()) {
                linux {
                    iconFile.set(appIconPng)
                }
                macOS {
                    iconFile.set(appIconPng)
                }
            }
            if (appIconIco.exists()) {
                windows {
                    iconFile.set(appIconIco)
                }
            }
        }
    }
}

// 开启警告 强报错
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}