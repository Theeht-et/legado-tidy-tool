package io.legado.app.help.source

import kotlin.coroutines.CoroutineContext

/**
 * 逐字照搬 upstream 的同名文件。
 *
 * [SuppressSourceNavigation] 是一个协程上下文元素：把它挂到某个抓取任务上时，
 * 该任务里的书源网页/视频跳转会被拦下，避免自动化抓取意外弹出交互页面或播放器。
 *
 * headless 里 [shouldSuppressSourceNavigation] 目前没有调用方（UI 跳转方法一律快速失败），
 * 但符号必须存在 —— WebBook 等引擎代码会 `withContext(SuppressSourceNavigation)`。
 */

/** Restrict only the operation carrying this element; parallel reader/login work stays interactive. */
internal object SuppressSourceNavigation : CoroutineContext.Element,
    CoroutineContext.Key<SuppressSourceNavigation> {
    override val key: CoroutineContext.Key<*> get() = this
}

internal fun shouldSuppressSourceNavigation(enabled: Boolean, context: CoroutineContext): Boolean =
    enabled && context[SuppressSourceNavigation] != null
