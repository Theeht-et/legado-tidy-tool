package io.legado.app.help.coroutine

import io.legado.app.utils.printOnDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext

/**
 * 链式协程。
 *
 * 与 upstream `help/coroutine/Coroutine.kt` 的差异（均为 headless 取舍）：
 *  1. 上游的默认 scope 是 `MainScope()`、默认 `executeContext` 是 `Dispatchers.Main` ——
 *     两者都依赖 Android 主线程 Looper，headless（纯 JVM）没有 Main dispatcher，
 *     一旦调用就会抛 `IllegalStateException: Module with the Main dispatcher had failed to initialize`。
 *     这里改成 `CoroutineScope(SupervisorJob() + Dispatchers.Default)` 与 `Dispatchers.Default`。
 *     对引擎调用方的影响：`WebBook` 里那些 `Coroutine.async(...)` 包装函数若沿用上游默认参数
 *     （引擎源码里 `executeContext: CoroutineContext = Dispatchers.Main`）仍会指向 Main，
 *     所以 headless 编排层**不要**调用这些包装函数，直接用 `*Await` 挂起函数（现状即是如此）。
 *  2. 其余逻辑（超时、onStart/onSuccess/onError/onFinally/onCancel、信号量）与上游逐字一致，
 *     保证「回调在协程作用域里派发」「主动取消不触发 onCancel」这些语义不变。
 *
 * 注意：如果协程太快完成，回调会不执行 —— 与上游同样的行为。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class Coroutine<T>(
    private val scope: CoroutineScope,
    context: CoroutineContext = Dispatchers.IO,
    private val startOption: CoroutineStart = CoroutineStart.DEFAULT,
    private val executeContext: CoroutineContext = Dispatchers.Default,
    private val semaphore: Semaphore? = null,
    block: suspend CoroutineScope.() -> T
) {

    companion object {

        // 上游是 MainScope()（Android 主线程），见类注释 1。
        private val DEFAULT = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun <T> async(
            scope: CoroutineScope = DEFAULT,
            context: CoroutineContext = Dispatchers.IO,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            executeContext: CoroutineContext = Dispatchers.Default,
            semaphore: Semaphore? = null,
            block: suspend CoroutineScope.() -> T
        ): Coroutine<T> {
            return Coroutine(scope, context, start, executeContext, semaphore, block)
        }

    }

    private val job: Job

    private var start: VoidCallback? = null
    private var success: Callback<T>? = null
    private var error: Callback<Throwable>? = null
    private var finally: VoidCallback? = null
    private var cancel: VoidCallback? = null

    private var timeMillis: Long? = null
    private var errorReturn: Result<T>? = null

    val isCancelled: Boolean
        get() = job.isCancelled

    val isActive: Boolean
        get() = job.isActive

    val isCompleted: Boolean
        get() = job.isCompleted

    init {
        this.job = executeInternal(context, block)
    }

    fun timeout(timeMillis: () -> Long): Coroutine<T> {
        this.timeMillis = timeMillis()
        return this@Coroutine
    }

    fun timeout(timeMillis: Long): Coroutine<T> {
        this.timeMillis = timeMillis
        return this@Coroutine
    }

    fun onErrorReturn(value: () -> T?): Coroutine<T> {
        this.errorReturn = Result(value())
        return this@Coroutine
    }

    fun onErrorReturn(value: T?): Coroutine<T> {
        this.errorReturn = Result(value)
        return this@Coroutine
    }

    fun onStart(
        context: CoroutineContext? = null,
        block: (suspend CoroutineScope.() -> Unit)
    ): Coroutine<T> {
        this.start = VoidCallback(context, block)
        return this@Coroutine
    }

    fun onSuccess(
        context: CoroutineContext? = null,
        block: suspend CoroutineScope.(T) -> Unit
    ): Coroutine<T> {
        this.success = Callback(context, block)
        return this@Coroutine
    }

    fun onError(
        context: CoroutineContext? = null,
        block: suspend CoroutineScope.(Throwable) -> Unit
    ): Coroutine<T> {
        this.error = Callback(context, block)
        return this@Coroutine
    }

    /**
     * 如果协程被取消，不执行
     */
    fun onFinally(
        context: CoroutineContext? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Coroutine<T> {
        this.finally = VoidCallback(context, block)
        return this@Coroutine
    }

    fun onCancel(
        context: CoroutineContext? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Coroutine<T> {
        this.cancel = VoidCallback(context, block)
        job.invokeOnCompletion {
            if (it is CancellationException && it !is ActivelyCancelException) {
                cancel()
            }
        }
        return this@Coroutine
    }

    //取消当前任务
    fun cancel(cause: ActivelyCancelException = ActivelyCancelException()) {
        if (!job.isCancelled) {
            job.cancel(cause)
        }
        cancel?.let {
            DEFAULT.launch(executeContext) {
                if (null == it.context) {
                    it.block.invoke(this)
                } else {
                    withContext(it.context) {
                        it.block.invoke(this)
                    }
                }
            }
        }
    }

    fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle {
        return job.invokeOnCompletion(handler)
    }

    fun start() {
        job.start()
    }

    private fun executeInternal(
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): Job {
        return (scope.plus(executeContext)).launch(start = startOption) {
            semaphore?.acquire()
            try {
                start?.let { dispatchVoidCallback(this, it) }
                ensureActive()
                val value = executeBlock(this, context, timeMillis ?: 0L, block)
                ensureActive()
                success?.let { dispatchCallback(this, value, it) }
            } catch (e: Throwable) {
                e.printOnDebug()
                val consume: Boolean = errorReturn?.value?.let { value ->
                    success?.let { dispatchCallback(this, value, it) }
                    true
                } ?: false
                if (!consume) {
                    error?.let { dispatchCallback(this, e, it) }
                }
            } finally {
                try {
                    finally?.let { dispatchVoidCallback(this, it) }
                } finally {
                    semaphore?.release()
                }
            }
        }
    }

    private suspend inline fun dispatchVoidCallback(scope: CoroutineScope, callback: VoidCallback) {
        if (null == callback.context) {
            callback.block.invoke(scope)
        } else {
            withContext(callback.context) {
                callback.block.invoke(this)
            }
        }
    }

    private suspend inline fun <R> dispatchCallback(
        scope: CoroutineScope,
        value: R,
        callback: Callback<R>
    ) {
        if (!scope.isActive) return
        if (null == callback.context) {
            callback.block.invoke(scope, value)
        } else {
            withContext(callback.context) {
                callback.block.invoke(this, value)
            }
        }
    }

    private suspend inline fun executeBlock(
        scope: CoroutineScope,
        context: CoroutineContext,
        timeMillis: Long,
        noinline block: suspend CoroutineScope.() -> T
    ): T {
        return withContext(context) {
            if (timeMillis > 0L) withTimeout(timeMillis) {
                block()
            } else {
                block()
            }
        }
    }

    private data class Result<out T>(val value: T?)

    private class VoidCallback(
        val context: CoroutineContext?,
        val block: suspend CoroutineScope.() -> Unit
    )

    private class Callback<VALUE>(
        val context: CoroutineContext?,
        val block: suspend CoroutineScope.(VALUE) -> Unit
    )
}
