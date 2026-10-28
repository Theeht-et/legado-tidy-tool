@file:Suppress("unused")

package io.legado.app.utils

/**
 * headless 版：upstream 的 LogUtils 依赖 android.util.Log / BuildConfig.DEBUG /
 * android.webkit.WebSettings / splitties appCtx / AppConfig，这里全部去掉。
 *
 * 只保留引擎直接 import 的两个符号：
 *  - `Throwable.printOnDebug()`（upstream 里受 BuildConfig.DEBUG 控制；
 *    headless 默认与 release 一致静默，`LEGADO_DEBUG=1` 时才打到 stderr）
 *  - `Throwable.stackTraceStr`（upstream 定义在 ThrowableExtensions.kt，
 *    engine 按同包 import 引用，文件名不影响解析）
 */

fun Throwable.printOnDebug() {
    if (DebugLog.enabled) printStackTrace()
}

val Throwable.stackTraceStr: String
    get() {
        val stackTrace = stackTraceToString()
        val lMsg = this.localizedMessage ?: "noErrorMsg"
        return when {
            stackTrace.isNotEmpty() -> stackTrace
            else -> lMsg
        }
    }
