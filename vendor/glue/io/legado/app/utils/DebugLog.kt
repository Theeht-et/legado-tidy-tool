package io.legado.app.utils

/**
 * 调试日志。上游实现是 `if (BuildConfig.DEBUG) Log.x(...)`。
 *
 * headless 差异：
 * - 没有 `android.util.Log` 平台桩，也没有 `BuildConfig`。这里在**开关打开时**写 stderr，
 *   关闭时（默认）与上游 **release 构建一致：什么都不做**（静默吞掉）。
 * - 开关：环境变量 `LEGADO_DEBUG` 或系统属性 `legado.debug` 为 true/1/yes 时打开。
 *   上游 `BuildConfig.DEBUG` 在用户安装的 release 包里恒为 false，所以默认 false 才是保真行为。
 */
@Suppress("unused")
object DebugLog {

    val enabled: Boolean = run {
        val raw = System.getenv("LEGADO_DEBUG") ?: System.getProperty("legado.debug")
        raw?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
    }

    fun e(tag: String, throwable: Throwable) {
        if (enabled) {
            log("E", tag, throwable.stackTraceToString())
        }
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            log("E", tag, if (throwable == null) msg else "$msg\n${throwable.stackTraceToString()}")
        }
    }

    fun d(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            log("D", tag, if (throwable == null) msg else "$msg\n${throwable.stackTraceToString()}")
        }
    }

    fun i(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            log("I", tag, if (throwable == null) msg else "$msg\n${throwable.stackTraceToString()}")
        }
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        if (enabled) {
            log("W", tag, if (throwable == null) msg else "$msg\n${throwable.stackTraceToString()}")
        }
    }

    private fun log(level: String, tag: String, msg: String) {
        System.err.println("[$level][$tag] $msg")
    }
}
