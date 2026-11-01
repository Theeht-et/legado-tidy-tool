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
    PARTIAL("部分通过，建议复核", 1),
    SKIPPED("本次未校验", 2),
    NO_SEARCH_URL("没有搜索入口，无法校验", 3),
    NO_EXPLORE_CATEGORY("没有可用发现分类，无法校验", 3),
    UNSUPPORTED("不支持自动校验", 4),
    PARSE_EMPTY("页面能打开但解析不出东西", 5),
    NEED_JS("用了本工具不支持的 JS 能力", 6),
    TIMEOUT("超时——降低并发或换代理", 7),
    NETWORK("网络不通——站点或代理问题", 8),
    HTTP_ERROR("站点返回错误状态码", 9),
    ERROR("其它错误", 10),
}

/** 单次校验中实际执行的上游阶段。 */
enum class ProbeStage(val label: String) {
    SEARCH("搜索"),
    EXPLORE_CATEGORIES("发现分类解析"),
    EXPLORE("发现分页"),
    BOOK_INFO("书籍详情"),
    TOC("目录"),
    CONTENT("正文"),
}

/** 阶段状态与整条校验结论分开，避免后续失败被折叠成搜索成功。 */
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
)

/** 校验入口：搜索或发现。两个入口随后复用相同的详情、目录和正文校验链。 */
enum class ProbeEntry { SEARCH, EXPLORE }

/** 校验深度：SEARCH 仅执行入口；WITH_TOC 继续详情与目录；FULL_CHAIN 再验证正文。 */
enum class ProbeDepth { SEARCH, WITH_TOC, FULL_CHAIN }

data class ProbeOptions(
    val depth: ProbeDepth = ProbeDepth.FULL_CHAIN,
    val timeoutMs: Long = 20_000,
    val keyword: String = "剑来",
    /** 本轮默认代理；书源 header.proxy 存在时以书源配置为准。 */
    val proxy: String? = null,
    val entry: ProbeEntry = ProbeEntry.SEARCH,
    /** 连续发现页上限；命中上游“本页无新增书籍即停止”规则时会提前结束。 */
    val maxExplorePages: Int = 3,
)

data class ProbeResult(
    val index: Int,
    val status: ProbeStatus,
    val httpCode: Int? = null,
    val bookCount: Int = 0,
    val elapsedMs: Long = 0,
    val detail: String? = null,
    val stageResults: List<ProbeStageResult> = emptyList(),
    /** Inputs that determine whether this result can be reused by a later validation run. */
    val entry: ProbeEntry = ProbeEntry.SEARCH,
    val depth: ProbeDepth = ProbeDepth.FULL_CHAIN,
    val keyword: String = "剑来",
    val timeoutMs: Long = 20_000,
    val maxExplorePages: Int = 3,
    /** Normalized per-validation proxy; source-level header.proxy can override it. */
    val proxy: String? = null,
) {
    val ok: Boolean get() = status == ProbeStatus.OK
}

/**
 * Ordered stages expected for one validation run. Discovery has a category parsing
 * prerequisite in addition to its paging stage.
 */
fun expectedProbeStages(entry: ProbeEntry, depth: ProbeDepth): List<ProbeStage> = buildList {
    if (entry == ProbeEntry.EXPLORE) {
        add(ProbeStage.EXPLORE_CATEGORIES)
        add(ProbeStage.EXPLORE)
    } else {
        add(ProbeStage.SEARCH)
    }
    if (depth != ProbeDepth.SEARCH) {
        add(ProbeStage.BOOK_INFO)
        add(ProbeStage.TOC)
    }
    if (depth == ProbeDepth.FULL_CHAIN) add(ProbeStage.CONTENT)
}

/** Explicitly records prerequisite-dependent stages that were not reached. */
fun notRunProbeStages(
    entry: ProbeEntry,
    depth: ProbeDepth,
    reason: String,
    alreadyRecorded: Set<ProbeStage> = emptySet(),
): List<ProbeStageResult> =
    expectedProbeStages(entry, depth)
        .filterNot { it in alreadyRecorded }
        .map { stage ->
            ProbeStageResult(stage, ProbeStageStatus.NOT_RUN, detail = reason)
        }

/** 引擎实现方在 core 侧的唯一接口，方便用假引擎写单测。 */
interface ProbeEngine {
    suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult
}