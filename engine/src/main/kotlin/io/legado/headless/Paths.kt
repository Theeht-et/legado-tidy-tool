package io.legado.headless

import java.io.File

/**
 * 兼容层：`vendor/glue`（平台补全层）里有九个文件直接引用了参考项目
 * `legado-tool-kotlin` 的 `io.legado.headless.Paths`。
 *
 * 与其改九处 import（还会在下次重新 vendor 时丢掉），不如保留这个小对象。
 * 我们只用得上 `cacheDir`：JS 分享作用域、ACache、解压临时目录都往它下面写。
 *
 * 目录布局：`%APPDATA%\legado-tidy-tool\cache`（Windows）/
 * `~/.cache/legado-tidy-tool`（其他系统）。不写进安装目录，因为便携包所在的
 * 目录可能只读（U 盘、Program Files）。
 */
object Paths {

    val userHome: File get() = File(System.getProperty("user.home") ?: ".")

    private val appData: File by lazy {
        val local = System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA")
        if (local != null) File(local, "legado-tidy-tool") else File(userHome, ".legado-tidy-tool")
    }

    val projectRoot: File by lazy {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var dir: File? = start
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        start
    }

    val runDir: File by lazy { File(projectRoot, "run").apply { mkdirs() } }

    /** 各类缓存（JS 分享作用域、ACache、解压临时目录……）。 */
    val cacheDir: File by lazy { File(appData, "cache").apply { mkdirs() } }
}
