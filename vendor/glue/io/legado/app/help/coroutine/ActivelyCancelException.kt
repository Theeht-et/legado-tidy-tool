package io.legado.app.help.coroutine

import kotlin.coroutines.cancellation.CancellationException

/**
 * 照抄 upstream `help/coroutine/ActivelyCancelException.kt`。
 *
 * 用来区分「用户主动取消」（本异常）与「协程内部因父级取消而取消」，
 * `Coroutine.onCancel` 只对后者触发。纯 Kotlin，无 Android 依赖。
 */
class ActivelyCancelException : CancellationException() {

    override fun fillInStackTrace(): Throwable {
        stackTrace = emptyArray()
        return this
    }

}
