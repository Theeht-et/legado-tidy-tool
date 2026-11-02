package io.github.theeht.legadotidy.probe


/**
 * 书源校验结论。UNCHECKABLE 对应 Legado 的 CheckSourceStatus.UNCHECKABLE，
 * 不等同于校验失败：上游遇到非 HTTP 地址时会继续其他检查，但最终标记为无法检测域名。
 */
enum class ProbeStatus(val label: String, val severity: Int) {
    OK("可用", 0),
    UNCHECKABLE("无法检测域名，其余校验未发现问题", 1),
    PARTIAL("部分通过，建议复核", 2),
    SKIPPED("本次未校验", 3),
    NO_SEARCH_URL("没有搜索入口，无法校验", 4),
    NO_EXPLORE_CATEGORY("没有可用发现分类，无法校验", 4),
    UNSUPPORTED("当前运行环境不支持此能力", 5),
    PARSE_EMPTY("页面能打开但解析不出东西", 6),
    NEED_JS("用了本工具不支持的 JS 能力", 7),
    TIMEOUT("超时——降低并发或换代理", 8),
    NETWORK("网络不通——站点或代理问题", 9),
    HTTP_ERROR("站点返回错误状态码", 10),
    ERROR("其它错误", 11),
}

/**
 * 一轮校验中记录的上游六个检查项目。
 * 书籍信息、目录和正文会分别对搜索结果及发现结果执行，因此允许出现多条同阶段记录。
 */
enum class ProbeStage(val label: String) {
    DOMAIN("域名检测"),
    SEARCH("搜索"),
    DISCOVERY("发现"),
    BOOK_INFO("书籍详情"),
    TOC("目录"),
    CONTENT("正文"),

}

enum class ProbeStageStatus(val label: String) {
    SUCCESS("成功"),
    EMPTY("结果为空"),
    FAILED("失败"),
    NOT_RUN("未执行"),
}

data class ProbeStageResult(
    val stage: ProbeStage,
    val status: ProbeStageStatus,
    val itemCount: Int = 0,
    val elapsedMs: Long = 0,
    val detail: String? = null,
    val failureStatus: ProbeStatus? = null,
    val httpCode: Int? = null,
    /** 同一书源的搜索结果和发现结果可能分别触发详情、目录、正文校验。 */
    val context: String? = null,
)

/**
 * 与 Legado CheckSource 的六个开关和默认值一致。
 * 一个 timeoutMs 包裹单个书源的整轮 doCheckSource；域名 Socket 检测另有上游 2 秒超时。
 */
data class ProbeOptions(
    val timeoutMs: Long = 180_000,
    /** 上游 CheckSource.keyword 默认值；书源 ruleSearch.checkKeyWord 可覆盖。 */
    val keyword: String = "我的",
    /** 本轮默认代理；书源 header.proxy 存在时仍由上游请求配置优先。 */
    val proxy: String? = null,
    val checkDomain: Boolean = false,
    val checkSearch: Boolean = true,
    val checkDiscovery: Boolean = true,
    val checkInfo: Boolean = true,
    val checkCategory: Boolean = true,
    val checkContent: Boolean = true,
    /** 对应上游 CheckSource.wSourceComment，默认记录错误注释。 */
    val writeErrorComment: Boolean = true,
)

/**
 * elapsedMs 与上游 BookSource.respondTime 对齐：失败时在实际整轮耗时上增加 timeoutMs。
 * actualElapsedMs 保留未经失败惩罚的真实执行时间，供详情诊断使用。
 */
data class ProbeResult(
    val index: Int,
    val status: ProbeStatus,
    val httpCode: Int? = null,
    val bookCount: Int = 0,
    val elapsedMs: Long = 0,
    val detail: String? = null,
    val stageResults: List<ProbeStageResult> = emptyList(),
    val keyword: String = "我的",
    val timeoutMs: Long = 180_000,
    val proxy: String? = null,
    val actualElapsedMs: Long = elapsedMs,
    val options: ProbeOptions = ProbeOptions(),
) {
    val ok: Boolean get() = status == ProbeStatus.OK || status == ProbeStatus.UNCHECKABLE
}

