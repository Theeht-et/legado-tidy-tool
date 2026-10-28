package androidx.media3.common

/**
 * 平台桩。引擎里只有 `AnalyzeUrl.getMediaItem()` 这个扩展函数用到它，
 * 而那条路径是给有声书播放器用的 —— headless 下永远不会被调用。
 */
class MediaItem
