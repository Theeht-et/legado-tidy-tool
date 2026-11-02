import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))

    // core 的公开 API 里有 JsonObject（BookSourceDoc.raw），engine 的入口是 suspend 函数，
    // 所以入口模块必须自己拿到 gson 与 coroutines —— 不能指望传递依赖暴露它们。
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

compose.desktop {
    application {
        mainClass = "io.github.theeht.legadotidy.desktop.MainKt"
    }
}

/**
 * 用 jlink + jpackage 生成 Windows 原生 GUI 启动器。
 *
 * 产物是一个可直接运行的应用目录：
 *   legado-tidy/
 *     legado-tidy.exe
 *     app/       应用及依赖 JAR、Skiko 原生 DLL/DAT
 *     runtime/   精简 JRE
 *
 * app-image 不依赖 WiX，也不是安装包；分发时必须保留整个目录。
 */
val bundledRuntime = layout.buildDirectory.dir("jlink/runtime")

val jlinkRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "用 jlink 生成精简 JRE"

    val out = bundledRuntime.get().asFile
    val javaHome = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }.get().metadata.installationPath.asFile
    val executableName = if (System.getProperty("os.name").lowercase().contains("windows")) {
        "jlink.exe"
    } else {
        "jlink"
    }

    doFirst {
        out.deleteRecursively()
        out.parentFile.mkdirs()
    }
    executable = File(javaHome, "bin/$executableName").absolutePath
    args(
        "--add-modules",
        listOf(
            "java.base", "java.desktop", "java.logging", "java.management", "java.naming",
            "java.net.http", "java.security.jgss", "java.sql", "java.xml", "java.transaction.xa",
            "jdk.crypto.ec", "jdk.unsupported", "jdk.charsets", "jdk.localedata", "jdk.zipfs",
        ).joinToString(","),
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress=2",
        "--output",
        out.absolutePath,
    )

    // jlink 在 Windows runtime/lib 中会包含供本机 C/C++ 开发链接使用的导入库。
    // jpackage 通过 EXE 启动器加载 JVM，不需要这两个链接文件；删除它们不影响
    // Java 类、JNI DLL 或运行时动态库。只针对 Windows 镜像执行，其他平台不处理。
    doLast {
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            val linkLibraries = listOf(
                File(out, "lib/jvm.lib"),
                File(out, "lib/jawt.lib"),
            )
            var removedBytes = 0L
            linkLibraries.forEach { file ->
                if (file.exists()) {
                    val size = file.length()
                    if (!file.delete()) {
                        throw GradleException("无法删除 JRE 开发链接库：${file.absolutePath}")
                    }
                    removedBytes += size
                    logger.lifecycle("已移除非运行时开发链接库：${file.name}（$size bytes）")
                }
            }
            logger.lifecycle("JRE 非运行时文件清理完成，释放 $removedBytes bytes。")
        }
    }
}

val windowsAppInput = layout.buildDirectory.dir("windows-app-input")

/**
 * jpackage 会把输入目录中的所有 JAR 放入 app/。
 * Skiko Runtime JAR 中的顶层 DLL/DAT 同时还需要以独立文件放入 app/，
 * 因此这里在复制时仅从 Skiko JAR 中移除这些顶层原生资源，保留其余类和资源。
 * 这样不改变运行时类路径，也避免同一份原生文件在 JAR 和目录中重复分发。
 */
val prepareWindowsAppInput by tasks.registering {
    group = "distribution"
    description = "准备 Windows app-image 输入并去除 Skiko JAR 中重复的原生资源"

    val runtimeClasspath = configurations.runtimeClasspath
    val desktopJar = tasks.named<Jar>("jar")
    dependsOn(desktopJar)
    inputs.files(runtimeClasspath)
    inputs.file(desktopJar.flatMap { it.archiveFile })
    outputs.dir(windowsAppInput)

    doLast {
        val appDir = windowsAppInput.get().asFile
        appDir.deleteRecursively()
        appDir.mkdirs()

        val runtimeJars = runtimeClasspath.get().files
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .sortedBy { it.name }
        val skikoJars = runtimeJars.filter { it.name.startsWith("skiko", ignoreCase = true) }
        if (skikoJars.isEmpty()) {
            throw GradleException("未解析到 Skiko JAR，无法准备 Windows 原生资源。")
        }

        val nativeResourceNames = linkedSetOf<String>()
        runtimeJars.forEach { source ->
            val target = File(appDir, source.name)
            if (source in skikoJars) {
                ZipFile(source).use { zip ->
                    ZipOutputStream(target.outputStream().buffered()).use { output ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val isRootNativeResource = !entry.isDirectory &&
                                !entry.name.contains('/') &&
                                (entry.name.endsWith(".dll", ignoreCase = true) ||
                                    entry.name.endsWith(".dat", ignoreCase = true))
                            val upperEntryName = entry.name.uppercase()
                            val isJarSignatureFile = upperEntryName.startsWith("META-INF/") && (
                                upperEntryName.endsWith(".SF") ||
                                    upperEntryName.endsWith(".RSA") ||
                                    upperEntryName.endsWith(".DSA") ||
                                    upperEntryName.endsWith(".EC") ||
                                    upperEntryName.substringAfterLast('/').startsWith("SIG-")
                                )

                            if (isRootNativeResource) {
                                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                                val nativeFile = File(appDir, entry.name)
                                if (nativeFile.exists()) {
                                    if (!nativeFile.readBytes().contentEquals(bytes)) {
                                        throw GradleException(
                                            "不同 Skiko JAR 含有同名但内容不同的原生资源：${entry.name}"
                                        )
                                    }
                                } else {
                                    nativeFile.writeBytes(bytes)
                                }
                                nativeResourceNames += entry.name
                            } else if (!isJarSignatureFile) {
                                val copiedEntry = ZipEntry(entry.name).apply {
                                    if (entry.time >= 0) time = entry.time
                                }
                                output.putNextEntry(copiedEntry)
                                if (!entry.isDirectory) {
                                    zip.getInputStream(entry).use { it.copyTo(output) }
                                }
                                output.closeEntry()
                            }
                        }
                    }
                }
            } else {
                source.copyTo(target, overwrite = true)
            }
        }

        if (nativeResourceNames.none { it.endsWith(".dll", ignoreCase = true) }) {
            throw GradleException("Skiko JAR 中没有找到可提取的 DLL。")
        }
        if (nativeResourceNames.none { it.endsWith(".dat", ignoreCase = true) }) {
            throw GradleException("Skiko JAR 中没有找到可提取的 DAT 资源。")
        }

        val appJar = desktopJar.get().archiveFile.get().asFile
        appJar.copyTo(File(appDir, appJar.name), overwrite = true)

        logger.lifecycle(
            "Windows app 输入已准备：${runtimeJars.size} 个依赖 JAR，" +
                "提取 ${nativeResourceNames.size} 个 Skiko 原生资源，原生资源不再重复保留在 JAR 中。"
        )
    }
}

/**
 * Windows 主机上运行：
 *   gradle :desktop:windowsAppImage
 *
 * jpackage 的 app-image 会生成真正的 legado-tidy.exe GUI 启动器，
 * 同时把精简 JRE 和应用依赖放入同一目录。
 */
val windowsAppImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "生成免安装 Windows 原生 EXE 应用目录"
    dependsOn(jlinkRuntime, prepareWindowsAppInput)

    val outputDir = layout.buildDirectory.dir("windows-app-image").get().asFile
    val appInputDir = windowsAppInput.get().asFile
    val runtimeDir = bundledRuntime.get().asFile
    val javaHome = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }.get().metadata.installationPath.asFile
    val appJarName = tasks.named<Jar>("jar").get().archiveFileName.get()

    executable = File(javaHome, "bin/jpackage.exe").absolutePath
    args(
        "--type", "app-image",
        "--name", "legado-tidy",
        "--app-version", "0.1.0",
        "--vendor", "Theeht",
        "--description", "书源去重、校验、整理",
        "--input", appInputDir.absolutePath,
        "--main-jar", appJarName,
        "--main-class", "io.github.theeht.legadotidy.desktop.MainKt",
        "--runtime-image", runtimeDir.absolutePath,
        "--dest", outputDir.absolutePath,
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xmx2g",
        "--java-options", "-Dskiko.library.path=\$APPDIR",
    )
    outputs.dir(outputDir)

    doFirst {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            throw GradleException("windowsAppImage 必须在 Windows 主机上运行。")
        }
        outputDir.deleteRecursively()
        outputDir.mkdirs()
    }

    doLast {
        val appImage = File(outputDir, "legado-tidy")
        val launcher = File(appImage, "legado-tidy.exe")
        val appConfig = File(appImage, "app/legado-tidy.cfg")
        val runtimeModules = File(appImage, "runtime/lib/modules")
        if (!launcher.isFile || !appConfig.isFile || !runtimeModules.isFile) {
            throw GradleException("jpackage 生成的 Windows app-image 不完整：${appImage.absolutePath}")
        }
        logger.lifecycle("Windows EXE 已生成：${launcher.absolutePath}")
    }
}
