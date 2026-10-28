package io.legado.app.help

import io.legado.app.utils.MD5Utils
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 规则大数据（大变量）存储。
 *
 * ## 与 upstream 的差异（降级说明）
 * 上游把书 / 章节 / 订阅的「大变量」（超过 10000 字的变量，见
 * `RuleDataInterface.putVariable`）落到 `appCtx.externalFiles/ruleData/...` 的磁盘目录里，
 * 键用 `MD5Utils.md5Encode` 后的 路径分层存放，并另有 `clearInvalid()` 走 `appDb`
 * 清理已删除书籍/订阅的残留目录。
 *
 * headless 里：
 *  1. 没有 Android 的 externalFiles 目录，也不想让「抓取对比」写一堆中间文件 ——
 *     改成**进程内的 ConcurrentHashMap**，键沿用上游的「逐段 MD5 再拼接」规则，
 *     只是存储介质从磁盘换成内存。
 *  2. `clearInvalid()`（清理删书残留）依赖 Room 数据库，headless 用不到，保留为 no-op。
 *
 * 对结果的影响：大变量只在**单次进程生命周期内**有效（上游跨进程持久化）。
 * 本工具是「一次性并发抓取」，同一个源的同一次抓取里 put/get 仍在同一进程，
 * 语义不受影响；进程结束后不留残留。
 */
object RuleBigDataHelp {

    private val store = ConcurrentHashMap<String, String>()

    /** 与上游一致：每一段 key 先 MD5，再用不可见分隔符拼成最终 key，避免超长 key。 */
    private fun composeKey(vararg parts: String): String =
        parts.joinToString("\u0000") { MD5Utils.md5Encode(it) }

    suspend fun clearInvalid() {
        // 上游清理磁盘上的失效目录 + 走 appDb 查询；headless 是内存存储，无残留可清。
    }

    fun putBookVariable(bookUrl: String, key: String, value: String?) {
        val compositeKey = composeKey(bookUrl, key)
        if (value == null) {
            store.remove(compositeKey)
        } else {
            store[compositeKey] = value
        }
    }

    fun getBookVariable(bookUrl: String, key: String?): String? {
        return store[composeKey(bookUrl, key.orEmpty())]
    }

    fun hasBookVariable(bookUrl: String, key: String): Boolean {
        return store.containsKey(composeKey(bookUrl, key))
    }

    fun putChapterVariable(bookUrl: String, chapterUrl: String, key: String, value: String?) {
        val compositeKey = composeKey(bookUrl, chapterUrl, key)
        if (value == null) {
            store.remove(compositeKey)
        } else {
            store[compositeKey] = value
        }
    }

    fun getChapterVariable(bookUrl: String, chapterUrl: String, key: String): String? {
        return store[composeKey(bookUrl, chapterUrl, key)]
    }

    /**
     * 上游把弹幕写成一个 `danmaku` 大变量文件后返回 `File`，供 `BookChapter.getDanmaku()` 读取。
     * headless 不在对比路径上，也没有磁盘缓存，返回 null —— 调用方会退回到 `variableMap` 分支。
     */
    fun getDanmakuFile(bookUrl: String, chapterUrl: String): File? {
        return null
    }

    fun putRssVariable(origin: String, link: String, key: String, value: String?) {
        val compositeKey = composeKey(origin, link, key)
        if (value == null) {
            store.remove(compositeKey)
        } else {
            store[compositeKey] = value
        }
    }

    fun getRssVariable(origin: String, link: String, key: String): String? {
        return store[composeKey(origin, link, key)]
    }
}
