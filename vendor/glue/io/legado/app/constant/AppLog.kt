package io.legado.app.constant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * headless 版 AppLog。
 *
 * upstream 依赖 android.util.Log / BuildConfig.DEBUG / EventBus / toastOnUi / AppConfig，
 * 这里全部去掉，实现为：
 *  - 写 stderr（带 `[AppLog]` 前缀，异常跟栈），方便容器日志采集；
 *  - 同时在内存里保留最近 100 条，供服务层查询。
 *
 * 公开签名（put / putNotSave / clear / exportText / logs / putDebug）与 upstream 保持一致，
 * 引擎按 `AppLog.put(message, throwable)` 调用。`toast` 参数保留但 headless 下被忽略。
 */
object AppLog {

    private const val MAX_LOGS = 100

    private val mLogs = arrayListOf<Triple<Long, String, Throwable?>>()

    val logs
        @Synchronized get() = mLogs.toList()

    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (mLogs.size >= MAX_LOGS) {
            mLogs.removeLastOrNull()
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        writeStderr(message, throwable)
    }

    @Synchronized
    fun putNotSave(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (mLogs.size >= MAX_LOGS) {
            mLogs.removeLastOrNull()
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        writeStderr(message, throwable)
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }

    fun exportText(entries: List<Triple<Long, String, Throwable?>>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return buildString {
            entries.asReversed().forEach { (time, message, throwable) ->
                append(dateFormat.format(Date(time)))
                append(' ')
                appendLine(message)
                throwable?.let {
                    appendLine(it.stackTraceToString().trimEnd().prependIndent("    "))
                }
            }
        }
    }

    fun putDebug(message: String?, throwable: Throwable? = null) {
        put(message, throwable)
    }

    private fun writeStderr(message: String, throwable: Throwable?) {
        System.err.println("[AppLog] $message")
        throwable?.printStackTrace()
    }
}
