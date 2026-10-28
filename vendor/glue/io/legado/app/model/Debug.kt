package io.legado.app.model

/**
 * headless 版 Debug。
 *
 * ## 为什么不直接搬上游的（23KB）
 * 上游 `Debug` 是为「在 App 里调试**单个**书源」设计的：它内部有
 * `if (debugSource != sourceUrl) return`，**只有当前正在调试的那一个源会收到回调**。
 * 而本工具是 500 个源并发抓取，所以：
 *
 *   ★ **进度事件不应该走 Debug**，而应该由抓取编排层在
 *     `searchBookAwait` / `getChapterListAwait` / `getContentAwait` 三个阶段边界上报。
 *     那样才对应前端契约里的 stage（search / toc / content）。
 *
 * 这里保留最小实现，只为了让引擎那 122 处 `Debug.log(...)` 调用能编译并有个去处。
 * 调用点大部分是诊断性质的（打印规则中间结果），默认丢弃；需要排查某个源时
 * 打开 [verbose] 即可全部落到 stderr。
 */
object Debug {

    /** 打开后所有 `Debug.log` 都会打到 stderr。排查单个书源时用。 */
    @Volatile
    var verbose: Boolean = false

    /** 只打印来自这个源（bookSourceUrl / 书名）的日志；为空表示全部。 */
    @Volatile
    var onlySource: String? = null

    /**
     * 上游用它做「书源调试 WebSocket」的回调。headless 下没有 UI，
     * 保留字段是为了让 `BookList.kt` 里的 `if (Debug.callback == null)` 判断成立
     * —— 那条分支会在没有回调时改走另一条更省事的路径。
     */
    @Volatile
    var callback: Callback? = null

    /** 上游的「校验书源」用；headless 恒为 false。 */
    @Volatile
    var isChecking: Boolean = false

    /** 上游给书源校验面板用的两个 map，保留类型以兼容引用。 */
    val debugMessageMap = HashMap<String, String>()
    val debugTimeMap = HashMap<String, Long>()

    fun log(
        sourceUrl: String?,
        msg: String = "",
        print: Boolean = true,
        isHtml: Boolean = false,
        showTime: Boolean = true,
        state: Int = 1
    ) {
        if (!verbose) return
        onlySource?.let { if (it != sourceUrl) return }
        System.err.println("[src:${sourceUrl ?: "-"}] ${if (isHtml) msg.replace(Regex("<[^>]+>"), "") else msg}")
    }

    fun log(msg: String?) {
        if (verbose) System.err.println(msg)
    }

    fun isDebugging(sourceUrl: String): Boolean = verbose && (onlySource == null || onlySource == sourceUrl)

    interface Callback {
        fun printLog(state: Int, msg: String)
    }
}
