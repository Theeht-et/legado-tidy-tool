package io.legado.app.model

/**
 * headless 版 VideoPlay。
 *
 * 上游是「视频播放器状态机」，依赖 GSYVideoPlayer / Activity 悬浮窗 / SharedPreferences。
 * 本工具不播放视频：书源脚本里的 `java.openVideoPlayer(...)` 已在
 * `JsExtensions` / `SourceHelp` 里直接抛 `UnsupportedOperationException` 快速失败
 * （见 `docs/PATCHES.md` §三），所以这里连入口都不需要，只留一个空对象占位，
 * 保证任何残留 import 能解析。
 */
@Suppress("unused")
object VideoPlay
