package io.github.theeht.legadotidy.probe

import io.github.theeht.legadotidy.model.BookSourceDoc

/**
 * 校验结果分类。
 *
 * 分类的唯一目的是「告诉用户下一步该干什么」，所以每一类都必须能对应一个动作，
 * 而不是一个技术异常名。顺序即严重度，也是排序依据。
 */
enum class ProbeStatus(val label: String, val severity: Int) {
    OK("可用", 0),
    SKIPPED("本次未校验", 1),           // 用户手动跳过 / 无 searchUrl
    NO_SEARCH_URL("没有搜索规则，校验不了", 2),
    UNSUPPORTED("不支持自动校验", 3),   // 统一：webView / 验证码 / 登录 / 滑块 / 播放器 / 外部跳转
    PARSE_EMPTY("页面能打开但解析不出东西", 4),
    NEED_JS("用了本工具不支持的 JS 能力", 5),
    TIMEOUT("超时——降低并发或换代理", 6),
    NETWORK("网络不通——站点或代理问题", 7),
    HTTP_ERROR("站点返回错误状态码", 8),
    ERROR("其它错误", 9),
}

/** 校验深度：默认只到搜索（成本最低、区分度最高）。 */
enum class ProbeDepth { SEARCH, WITH_TOC }

data class ProbeOptions(
    val depth: ProbeDepth = ProbeDepth.SEARCH,
    val timeoutMs: Long = 20_000,
    val keyword: String = "剑来",
    val proxy: String? = null,
)

data class ProbeResult(
    val index: Int,
    val status: ProbeStatus,
    val httpCode: Int? = null,
    val bookCount: Int = 0,
    val elapsedMs: Long = 0,
    val detail: String? = null,   // UNSUPPORTED 时记录具体原因：webView / 验证码 / 登录 / 播放器 / 外部跳转
) {
    val ok: Boolean get() = status == ProbeStatus.OK
}

/** 引擎实现方在 core 侧的唯一接口，方便用假引擎写单测。 */
interface ProbeEngine {
    suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult
}