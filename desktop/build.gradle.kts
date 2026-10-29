import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

        nativeDistributions {
            // AppImage = 免安装目录（解压即用），不需要 WiX。
            targetFormats(TargetFormat.AppImage)
            packageName = "legado-tidy"
            packageVersion = "0.1.0"
            description = "书源去重、校验、整理"
            vendor = "Theeht"
            // 便携目录不要黑窗：用 javaw 启动的口令在 launcher 侧配置。
            windows {
                menu = false
                shortcut = false
                dirChooser = false
                perUserInstall = true
                upgradeUuid = "9f1c0a53-5b6e-4d47-9a1f-2f7bd4f4a6d1"
            }
        }
    }
}

/**
 * 命令行跑一遍（不进界面）：
 *
 *   gradle :desktop:cli -Pargs="--in sources.json --limit 20"
 *
 * 不用 application 插件，是因为它和 Compose 桌面插件都要注册 `run`，名字会撞。
 * 路径里带空格时改用 `--in="C:/a b/x.json"` 这种等号写法。
 */
tasks.register<JavaExec>("cli") {
    group = "application"
    description = "命令行模式跑一次"
    mainClass.set("io.github.theeht.legadotidy.desktop.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    val raw = (project.findProperty("args") as String?)?.trim().orEmpty()
    if (raw.isNotEmpty()) {
        args = raw.split(' ').filter { it.isNotEmpty() }
    }
}

/**
 * 便携目录：免安装、双击即用。
 *
 * 为什么不用 jpackage：它在本机对 dest 目录的可写性检查恒失败（换到 TEMP 也一样），
 * 所以这里自己组装，结构：
 *
 *   legado-tidy/
 *     runtime/          精简 JRE（jlink，带 java.exe）
 *     app/              全部 jar
 *     legado-tidy.bat   带控制台（排障看日志用）
 *     打开书源整理.vbs  无黑窗（日常用这个）
 *
 * 模块清单是「跑起来需要什么」而不是「全都带上」：
 * - java.desktop   Compose/AWT
 * - jdk.unsupported sun.misc.Unsafe（Skiko、okhttp 都要）
 * - jdk.crypto.ec  TLS 的 ECDHE 套件
 * - jdk.charsets   GBK/GB18030 —— 中文书源站大量在用，缺了会乱码
 * - java.naming / java.management / java.sql / java.xml / java.logging：传递依赖要
 */
val portableRuntime = layout.buildDirectory.dir("jlink/runtime")

val jlinkRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "用 jlink 生成精简 JRE"
    val out = portableRuntime.get().asFile
    val javaHome = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }.get().metadata.installationPath.asFile
    val exe = if (System.getProperty("os.name").lowercase().contains("windows")) "jlink.exe" else "jlink"

    doFirst { out.deleteRecursively(); out.parentFile.mkdirs() }
    executable = File(javaHome, "bin/$exe").absolutePath
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
}

val portable by tasks.registering(Sync::class) {
    group = "distribution"
    description = "组装便携目录（免安装）"
    dependsOn(jlinkRuntime, tasks.named("jar"))
    into(layout.buildDirectory.dir("portable/legado-tidy"))

    from(portableRuntime) { into("runtime") }
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }) { into("app") }
    from(tasks.named("jar")) { into("app") }
    from("src/main/portable")

    // Skiko 的 dll 从 jar 里解出来单独放：精简 JRE 里它自己往 temp 解压会失败。
    doLast {
        val appDir = layout.buildDirectory.dir("portable/legado-tidy/app").get().asFile
        appDir.listFiles { f -> f.name.startsWith("skiko") && f.name.endsWith(".jar") }?.forEach { skikoJar ->
            copy {
                from(zipTree(skikoJar)) { include("*.dll", "*.dat") }
                into(appDir)
            }
        }
    }
}
