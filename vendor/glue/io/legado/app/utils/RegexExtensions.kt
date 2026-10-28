package io.legado.app.utils

import androidx.annotation.Keep
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceBook
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.JsEncodeUtils
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.analyzeRule.RuleData
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 带有超时检测的正则替换。
 *
 * 照抄 upstream `utils/RegexExtensions.kt`：
 *  - `@js:` 前缀判定、`replacement.substring(4)`、Rhino 求值、`quoteReplacementJs` 转义、
 *    `matcher.appendReplacement` / `appendTail` 循环，全部逐字保留（这些直接决定正文内容）。
 *  - 超时用 upstream 同一套 `Coroutine.async` + `select { job.onJoin{}; onTimeout{} }`，
 *    超时后抛 [RegexTimeoutException]（`NoStackTraceException` 子类），由调用方
 *    `ContentProcessor` 转成「停用该规则」的降级。
 *
 * 与 upstream 的差异（Android 平台降级，均只影响超时后的收尾动作，不影响正常替换结果）：
 *  1. 超时分支里去掉了 `appCtx.longToastOnUi(...)`（无 UI）与
 *     `CrashHandler.saveCrashInfo2File(...)`（headless 无崩溃上报）。
 *     上游在此重启应用；headless 没有可重启的 App，改为「只抛异常」。
 *  2. 上游超时后再等 3 秒（`onTimeout(3000) { appCtx.restart() }`）才重启。
 *     headless 不需要也不能重启进程，故这段等待整体删除。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun CharSequence.replace(
    name: String,
    regex: Regex,
    replacement: String,
    timeout: Long,
    chapter: BookChapter? = null,
    book: ReplaceBook? = null,
    includeContentInTimeoutMessage: Boolean = true,
): String {
    val charSequence = this@replace
    val isJs = replacement.startsWith("@js:")
    val replacement1 = if (isJs) replacement.substring(4) else replacement
    val reJsExtensions by lazy { RegexJsExtensions(name) }
    return runBlocking {
        suspendCancellableCoroutine { block ->
            Coroutine.async(executeContext = IO) {
                val job = launch {
                    try {
                        val pattern = regex.toPattern()
                        val matcher = pattern.matcher(charSequence)
                        val stringBuffer = StringBuffer()
                        while (matcher.find()) {
                            if (isJs) {
                                val jsResult = RhinoScriptEngine.run {
                                    val bindings = ScriptBindings()
                                    bindings["result"] = matcher.group()
                                    bindings["chapter"] = chapter
                                    bindings["book"] = book
                                    bindings["java"] = reJsExtensions
                                    eval(replacement1, bindings)
                                }.toString()
                                val quotedResult = jsResult.quoteReplacementJs()
                                matcher.appendReplacement(stringBuffer, quotedResult)
                            } else {
                                matcher.appendReplacement(stringBuffer, replacement1)
                            }
                        }
                        matcher.appendTail(stringBuffer)
                        block.resume(stringBuffer.toString())
                    } catch (e: Exception) {
                        block.resumeWithException(e)
                    }
                }
                select {
                    job.onJoin {}
                    onTimeout(timeout) {
                        val content = if (includeContentInTimeoutMessage) {
                            "\n替换内容:$charSequence"
                        } else {
                            ""
                        }
                        val timeoutMsg =
                            "替换超时,3秒后还未结束将重启应用\n规则名称:$name\n替换规则:$regex$content"
                        val exception = RegexTimeoutException(timeoutMsg)
                        block.cancel(exception)
                        // upstream 在此处还会 longToastOnUi + 写崩溃日志，并等 3 秒后 appCtx.restart()。
                        // headless 无 UI / 无崩溃上报 / 无可重启的 App，故只保留「抛异常」这一语义。
                    }
                }
            }
        }
    }
}

/**
 * JS 替换规则里 `java` 变量暴露给脚本的对象（`java.t2s` / `java.put(...)` 等）。
 *
 * headless 里没有独立的 `help/RegexJsExtensions.kt`，按 upstream
 * `help/RegexJsExtensions.kt` 逐字补齐后放在本文件（本次改动的写入范围只允许本文件）。
 * `log` / `logType` / `t2s` / `s2t` / `get` / `put` 与 upstream 一致；
 * 加解密方法由 [JsEncodeUtils] 提供。
 */
@Keep
@Suppress("unused")
class RegexJsExtensions(private val name: String) : JsEncodeUtils {

    private val ruleData by lazy { RuleData() }

    /**
     * 输出调试日志
     */
    fun log(msg: Any?): Any? {
        AppLog.putDebug("替换净化规则 $name 输出: $msg")
        return msg
    }

    /**
     * 输出对象类型
     */
    fun logType(any: Any?) {
        if (any == null) {
            log("null")
        } else {
            log(any.javaClass.name)
        }
    }

    fun t2s(text: String): String {
        return ChineseUtils.t2s(text)
    }

    fun s2t(text: String): String {
        return ChineseUtils.s2t(text)
    }

    fun get(key: String): String {
        return ruleData.getVariable(key).takeIf { it.isNotEmpty() } ?: ""
    }

    fun put(key: String, value: String): String {
        ruleData.putVariable(key, value)
        return value
    }
}
