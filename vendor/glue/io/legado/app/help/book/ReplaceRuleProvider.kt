package io.legado.app.help.book

import io.legado.app.data.entities.ReplaceRule

/**
 * 正文/标题「替换净化规则」的来源。
 *
 * ## 为什么有它
 * 上游 `ContentProcessor` 直接从 `appDb.replaceRuleDao` 取用户启用的净化规则。headless
 * 里**没有 Room 数据库、也不该有**：这些 ReplaceRule 是用户在 App 里的个人净化配置，
 * 不在书源 JSON 里。本工具要判断「哪个书源的正文更完整」，若默认套用用户私有规则，
 * 两个源「看上去差异很大」可能只是某人开了某条净化 —— 结论就不可信了。
 * 所以把「用户配置的来源」抽成这个 provider，headless 实现恒为空。
 *
 * 这**不是**砍能力：书源自带的正文规则（`contentRule.replaceRegex`）照常生效，
 * 见 `BatchContentContext.applyContentReplace` 与引擎的 `AnalyzeRule`。
 * 若将来要支持用户的净化规则，从这里注入即可，`ContentProcessor` 无需改动。
 */
object ReplaceRuleProvider {

    /** 作用于标题的启用规则。headless：无用户配置，返回空。 */
    fun findEnabledByTitleScope(bookName: String, bookOrigin: String): List<ReplaceRule> = emptyList()

    /** 作用于正文的启用规则。headless：无用户配置，返回空。 */
    fun findEnabledByContentScope(bookName: String, bookOrigin: String): List<ReplaceRule> = emptyList()

    /**
     * 规则替换超时后由 `ContentProcessor` 调用的禁用钩子（上游是 `replaceRuleDao.enable(id, false)`）。
     * headless 没有规则库存，no-op。
     */
    fun disable(id: Long) {
    }
}
