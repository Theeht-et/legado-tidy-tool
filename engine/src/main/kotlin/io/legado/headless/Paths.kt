package io.legado.headless

import java.io.File

/**
 * 兼容层：`vendor/glue`（平台补全层）里有九个文件直接引用了参考项目
 * `legado-tool-kotlin` 的 `io.legado.headless.Paths`。
 *
 * 与其改九处 import（下次重新 vendor 时还会丢），不如保留这个小对象。
 * 我们只用得上 `cacheDir`：JS 分享作用域、ACache、解压临时目录都往它下面写。
 *
 * 目录布局：`%LOCALAPPDATA%\legado-tidy-tool\cache`（Windows）/
 * `~/.legado-tidy-tool/cache`（其他系统）。不写进安装目录——便携包所在的
 * 目录可能只读（U 盘、Program Files）。
 */
object Paths {

    val userHome: File get() = File(System.getProperty("user.home") ?: ".")

    private val appData: File by lazy {
        val local = System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA")
        if (local != null) File(local, "legado-tidy-tool") else File(userHome, ".legado-tidy-tool")
    }

    val projectRoot: File by lazy { locateRoot() }

    val runDir: File by lazy { File(projectRoot, "run").apply { mkdirs() } }

    /** 各类缓存（JS 分享作用域、ACache、解压临时目录……）。 */
    val cacheDir: File by lazy { File(appData, "cache").apply { mkdirs() } }

    private fun locateRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var dir: File? = start
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        return start
    }
}
