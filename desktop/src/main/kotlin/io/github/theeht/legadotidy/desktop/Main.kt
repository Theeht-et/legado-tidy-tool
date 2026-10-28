package io.github.theeht.legadotidy.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.theeht.legadotidy.desktop.ui.TidyApp
import kotlin.system.exitProcess

/**
 * 唯一入口，两条路：
 * - 无参数（或带 `--gui`）→ 图形界面；
 * - 带任何其它参数 → 命令行。
 *
 * 不在启动时判断「有没有图形环境」：这个程序只做 Windows 桌面版，
 * 没有 X11/headless 的部署场景，猜反而会躲掉真正的启动错误。
 */
fun main(args: Array<String>) {
    val gui = args.isEmpty() || args.contains("--gui")
    if (!gui) {
        exitProcess(runCli(args))
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "书源整理") {
            TidyApp()
        }
    }
}
