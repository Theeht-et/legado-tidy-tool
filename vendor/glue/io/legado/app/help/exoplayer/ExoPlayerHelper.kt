package io.legado.app.help.exoplayer

import androidx.media3.common.MediaItem

/**
 * 平台桩：上游 [ExoPlayerHelper] 是基于 `androidx.media3` 的音频播放器。
 *
 * headless 完全不播放音频，只保留 `AnalyzeUrl.getMediaItem()` 实际用到的那一个方法，
 * 且方法体**立即抛异常**、绝不阻塞 —— 抓取池是几百并发，任何挂起都会拖垮整体。
 * 上游 `getMediaItem()` 只会被播放器代码调用，headless 里不会走到这里。
 */
object ExoPlayerHelper {

    fun createMediaItem(url: String, headers: Map<String, String>): MediaItem {
        throw UnsupportedOperationException("headless 不提供音频播放：createMediaItem($url)")
    }
}
