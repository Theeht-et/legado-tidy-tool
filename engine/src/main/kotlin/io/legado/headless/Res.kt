package io.legado.headless

import io.legado.app.R

/**
 * 引擎取字符串资源的落地实现。
 *
 * 文案与 upstream `app/src/main/res/values/strings.xml` 逐字一致 ——
 * 这些字符串会出现在异常消息里，最终进到对比报告的「结论 / 标记」列，
 * 改动会让 headless 的报错和 App 对不上。
 */
object Res {
    private val table: Map<Int, String> = mapOf(
        R.string.error_get_web_content to "Failed to access website:%s",
        R.string.chapter_list_empty to "Chapters is empty",
        R.string.replace_rule_invalid to "Pattern rule is empty or does not conform the regex specification.",
        R.string.intro_show to "Description:%s",
        R.string.intro_show_null to "Description: no introduction",
    )

    fun get(id: Int): String = table[id] ?: "res#$id"

    fun get(id: Int, vararg args: Any?): String {
        val fmt = table[id] ?: return "res#$id"
        return if (args.isEmpty()) fmt else runCatching { String.format(fmt, *args) }
            .getOrElse { fmt }
    }
}
