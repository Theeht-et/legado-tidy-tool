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
