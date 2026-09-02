import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.github.kuangcp"
version = "1.4.4"

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
            windows {
                // Keep this UUID stable forever. Windows Installer uses it to detect
                // that a newer MSI should upgrade the existing api-x installation.
                
                upgradeUuid = "7f3f1ab5-8205-4db2-90b4-6f2c5fdd8e7d"

                msiPackageVersion = packageVersion
                menuGroup = "Api-X"
                shortcut = true
                // 设置为false后就会自动安装到C盘程序, 能实现不选择目录就升级, 但是少了选目录的功能, 所以不能关, 但是这样就是会有点迷惑的提示说目录已存在 点确认即可
                // dirChooser = false
                if (appIconIco.exists()) {
                    iconFile.set(appIconIco)
                }
            }
        }
    }
}

// 生成版本信息（含 git commit hash）
val generatedVersionDir = layout.buildDirectory.dir("generated/version/kotlin")

val generateVersion by tasks.registering {
    val outputDir = generatedVersionDir.get()
    outputs.cacheIf { true }
    doLast {
        outputDir.asFile.mkdirs()
        val hash = try {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(project.rootDir)
                .start()
            proc.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "unknown" }

        val file = outputDir.file("app/build/Version.kt").asFile
        file.parentFile.mkdirs()
        val content = """
            |package app.build
            |
            |object Version {
            |    const val COMMIT = "${hash}"
            |    const val NAME = "${project.version}"
            |}
        """.trimMargin()
        if (!file.exists() || file.readText() != content) {
            file.writeText(content)
        }
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generatedVersionDir)
}

tasks.matching { it.name == "compileKotlin" }.configureEach {
    dependsOn(generateVersion)
}

// 开启警告 强报错
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// ---------- MSI 签名 ----------
// 用法: gradle signMsi
// 证书配置: 项目根目录放 cert.pfx + gradle.properties 中写 signCertPassword=xxx
// 签名工具: 使用 osslsigncode（下载: https://github.com/mtrojnar/osslsigncode/releases）
//   放到 PATH 中，或在 gradle.properties 中设置 signToolPath=C:\\path\\to\\osslsigncode.exe
val signCertPfx = layout.projectDirectory.file("cert.pfx").asFile
val signCertPassword: String = project.findProperty("signCertPassword")?.toString() ?: ""

val signMsi by tasks.registering {
    dependsOn("packageMsi")
    description = "Sign the MSI package with osslsigncode"
    group = "distribution"

    doLast {
        if (!signCertPfx.exists()) {
            logger.warn("cert.pfx not found at ${signCertPfx.absolutePath}, skipping signing")
            return@doLast
        }
        if (signCertPassword.isEmpty()) {
            logger.warn("signCertPassword not set in gradle.properties, skipping signing")
            return@doLast
        }

        val msiDir = layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile
        val msiFile = msiDir.listFiles()?.firstOrNull { it.extension == "msi" }
            ?: error("No MSI file found in $msiDir")

        val tool = findSignTool()
        if (tool == null) {
            logger.error("osslsigncode not found. Download from https://github.com/mtrojnar/osslsigncode/releases or set signToolPath in gradle.properties")
            return@doLast
        }

        val signedFile = File(msiDir, msiFile.nameWithoutExtension + "-signed.msi")
        logger.lifecycle("Signing ${msiFile.name} with ${tool.name} ...")

        val cmd = listOf(
            tool.absolutePath, "sign",
            "-pkcs12", signCertPfx.absolutePath,
            "-pass", signCertPassword,
            "-h", "sha256",
            "-in", msiFile.absolutePath,
            "-out", signedFile.absolutePath,
        )
        logger.lifecycle("  > ${cmd.joinToString(" ") { if (it.contains(' ') || it.contains('\\')) "\"$it\"" else it }}")

        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()

        if (proc.exitValue() == 0 && signedFile.exists()) {
            // 用签名后的文件替换原文件
            val backupFile = File(msiDir, msiFile.name + ".unsigned")
            msiFile.renameTo(backupFile)
            signedFile.renameTo(msiFile)
            backupFile.delete()
            logger.lifecycle("MSI signed successfully: ${msiFile.name}")
            logger.lifecycle(output)
        } else {
            signedFile.delete()
            error("osslsigncode failed (exit code ${proc.exitValue()}):\n$output")
        }
    }
}

fun findSignTool(): File? {
    // 1. gradle.properties 中手动指定
    val customPath = project.findProperty("signToolPath")?.toString()
    if (!customPath.isNullOrBlank()) {
        val f = File(customPath)
        if (f.exists()) return f
    }
    // 2. PATH 中查找 osslsigncode
    val which = try {
        val p = ProcessBuilder("where", "osslsigncode.exe").start()
        val result = p.inputStream.bufferedReader().readLine()
        p.waitFor()
        if (p.exitValue() == 0 && result != null) File(result.trim()) else null
    } catch (_: Exception) { null }
    if (which != null) return which
    // 3. 常见安装路径
    val candidates = listOf(
        System.getenv("LOCALAPPDATA") + "\\osslsigncode\\osslsigncode.exe",
        "C:\\Program Files\\osslsigncode\\osslsigncode.exe",
        System.getProperty("user.home") + "\\Downloads\\bin\\osslsigncode.exe",
    )
    return candidates.map { File(it) }.firstOrNull { it.exists() }
}