package io.legado.app.help.book

import io.legado.app.data.entities.ReplaceRule

/**
 * `ContentProcessor.getContent(...)` 的返回体，逐字照抄 upstream `help/book/BookContent.kt`。
 * 纯数据类，无依赖。
 */
data class BookContent(
    val sameTitleRemoved: Boolean,
    val textList: List<String>,
    //起效的替换规则
    val effectiveReplaceRules: List<ReplaceRule>?
) {

    override fun toString(): String {
        return textList.joinToString("\n")
    }

}
