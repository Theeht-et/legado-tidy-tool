package io.github.theeht.legadotidy.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.theeht.legadotidy.desktop.ui.TidyApp

/**
 * jpackage 的 GUI 唯一入口。保留 main(String[]) 签名以供 Windows 原生启动器调用；
 * 程序不再解析或提供命令行参数模式。
 */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    application {
        // 初次启动直接使用显示器可用工作区，充分利用 2K/高分辨率屏幕；
        // 用户仍可通过系统窗口按钮还原、调整大小。
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(placement = WindowPlacement.Maximized),
            title = "书源整理",
        ) {
            TidyApp()
        }
    }
}
