@file:Suppress("unused")

package io.legado.app.utils

/**
 * headless 版：没有 Android 的 Looper / Handler。HTTP 服务只跑单主线程事件循环，
 * 所以「当前线程」恒为「主线程」。
 *
 * 注意：AnalyzeRule.getWebJsResult() 里有 `if (isMainThread) error("webJs must be
 * called on a background thread")` —— 恒 true 意味着 headless 下 webJs 规则直接
 * 报错（webView 在 JVM 里本就无法执行），这是预期行为。
 */
val isMainThread: Boolean get() = true