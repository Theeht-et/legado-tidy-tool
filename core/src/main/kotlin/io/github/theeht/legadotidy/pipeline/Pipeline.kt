package io.github.theeht.legadotidy.pipeline

import io.github.theeht.legadotidy.dedup.DedupConfig
import io.github.theeht.legadotidy.dedup.DedupReport
import io.github.theeht.legadotidy.dedup.Deduper
import io.github.theeht.legadotidy.export.ExportConfig
import io.github.theeht.legadotidy.export.ExportResult
import io.github.theeht.legadotidy.export.Exporter
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.organize.OrganizeConfig
import io.github.theeht.legadotidy.organize.OrganizeReport
import io.github.theeht.legadotidy.organize.Organizer
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

enum class Phase(val label: String) {
    PROBE("校验"),
    DEDUP("去重"),
    ORGANIZE("整理"),
    EXPORT("导出"),
}

sealed interface PipelineEvent {
    data class PhaseStarted(val phase: Phase, val total: Int) : PipelineEvent
    data class Progress(
        val phase: Phase,
        val done: Int,
        val total: Int,
        val ok: Int = 0,
        val failed: Int = 0,
    ) : PipelineEvent

    data class PhaseFinished(val phase: Phase, val elapsedMs: Long, val note: String = "") : PipelineEvent
    data class Log(val message: String) : PipelineEvent
    data class Failed(val phase: Phase, val message: String, val cause: String? = null) : PipelineEvent
}

data class PipelineConfig(
    val dedup: DedupConfig = DedupConfig(),
    val probe: ProbeOptions = ProbeOptions(),
    val organize: OrganizeConfig = OrganizeConfig(),
    val export: ExportConfig = ExportConfig(),
    /** 并发上限。超过 200 后挠吐提升有限，超时却会成批出现。 */
    val concurrency: Int = 200,
    val skipProbe: Boolean = false,
    /** null 表示不导出（只跑前三个阶段）。 */
    val exportDir: Path? = null,
    val exportFileName: String? = null,
    /** 进度回调粒度：每多少源报一次。 */
    val progressEvery: Int = 20,
)

data class PipelineResult(
    val total: Int,
    val dedup: DedupReport,
    val probeResults: Map<Int, ProbeResult>,
    val organize: OrganizeReport,
    val exported: ExportResult?,
) {
    val okCount: Int get() = probeResults.values.count { it.status == ProbeStatus.OK }
}

/**
 * S1 → S2 → S3 → S4 的编排。
 *
 * 只管「顺序、进度、并发」；具体怎么探测由 [ProbeEngine] 提供。
 * 单源失败只记录，不中断整轮；阶段异常会包成 [PipelineEvent.Failed] 再抛出。
 */
class Pipeline(private val engine: ProbeEngine?) {

    suspend fun run(
        sources: List<BookSourceDoc>,
        config: PipelineConfig = PipelineConfig(),
        emit: suspend (PipelineEvent) -> Unit = { _ -> },
    ): PipelineResult = coroutineScope {
        val events = Channel<PipelineEvent>(Channel.BUFFERED)
        val pump = launch { for (e in events) emit(e) }

        try {
            // ---- S1 校验：所有导入书源（包括重复候选）都先获得独立结果 ----
            events.send(PipelineEvent.PhaseStarted(Phase.PROBE, sources.size))
            var mark = TimeSource.Monotonic.markNow()
            val probeResults: Map<Int, ProbeResult> = when {
                config.skipProbe || engine == null -> sources.indices.associateWith { i ->
                    ProbeResult(i, ProbeStatus.SKIPPED, detail = "已跳过校验")
                }
                else -> probeAll(sources, config, events)
            }
            val ok = probeResults.values.count { it.status == ProbeStatus.OK }
            events.send(
                PipelineEvent.PhaseFinished(
                    Phase.PROBE,
                    mark.elapsedNow().inWholeMilliseconds,
                    if (config.skipProbe || engine == null) "已跳过" else "可用 $ok/${probeResults.size}",
                )
            )

            // ---- S2 去重：将校验结果用于重复组默认保留建议 ----
            events.send(PipelineEvent.PhaseStarted(Phase.DEDUP, sources.size))
            mark = TimeSource.Monotonic.markNow()
            val dedup = Deduper.run(sources, config.dedup, probeResults)
            events.send(
                PipelineEvent.PhaseFinished(
                    Phase.DEDUP,
                    mark.elapsedNow().inWholeMilliseconds,
                    "重复组 ${dedup.groups.size}，建议保留 ${dedup.keepCount}/${dedup.total}",
                )
            )

            // ---- S3 整理 ----
            events.send(PipelineEvent.PhaseStarted(Phase.ORGANIZE, sources.size))
            mark = TimeSource.Monotonic.markNow()
            val organize = Organizer.run(sources, probeResults, config.organize)
            events.send(
                PipelineEvent.PhaseFinished(
                    Phase.ORGANIZE, mark.elapsedNow().inWholeMilliseconds, "分组 ${organize.pools.size}",
                )
            )

            // ---- S4 导出 ----
            var exported: ExportResult? = null
            val dir = config.exportDir
            if (dir != null) {
                events.send(PipelineEvent.PhaseStarted(Phase.EXPORT, sources.size))
                mark = TimeSource.Monotonic.markNow()
                val raws = sources.map { it.raw }
                val name = config.exportFileName ?: Exporter.defaultFileName()
                val result = Exporter.write(raws, organize.sources, dir, name, config.export)
                exported = result
                events.send(
                    PipelineEvent.PhaseFinished(
                        Phase.EXPORT, mark.elapsedNow().inWholeMilliseconds,
                        "${result.count} 个源 → ${result.file}",
                    )
                )
            }

            PipelineResult(sources.size, dedup, probeResults, organize, exported)
        } catch (e: Exception) {
            events.send(PipelineEvent.Failed(Phase.EXPORT, "流水线中断：${e.message}", e.toString()))
            throw e
        } finally {
            events.close()
            pump.join()
        }
    }

    private suspend fun probeAll(
        sources: List<BookSourceDoc>,
        config: PipelineConfig,
        events: Channel<PipelineEvent>,
    ): Map<Int, ProbeResult> = coroutineScope {
        val probeEngine = engine ?: return@coroutineScope emptyMap()
        val gate = Semaphore(config.concurrency.coerceIn(1, 2000))
        val done = AtomicInteger()
        val ok = AtomicInteger()
        val failed = AtomicInteger()
        val results = arrayOfNulls<ProbeResult>(sources.size)

        val jobs = sources.mapIndexed { i, source ->
            async(Dispatchers.Default) {
                val missingEntry = when (config.probe.entry) {
                    io.github.theeht.legadotidy.probe.ProbeEntry.SEARCH -> !source.canSearch
                    io.github.theeht.legadotidy.probe.ProbeEntry.EXPLORE -> !source.canExplore
                }
                val result = if (missingEntry) {
                    val (status, detail) = when (config.probe.entry) {
                        io.github.theeht.legadotidy.probe.ProbeEntry.SEARCH ->
                            ProbeStatus.NO_SEARCH_URL to "未配置上游搜索入口（普通源需要 searchUrl，JS 源需要 mainJs）"
                        io.github.theeht.legadotidy.probe.ProbeEntry.EXPLORE ->
                            ProbeStatus.NO_EXPLORE_CATEGORY to "未配置上游发现入口（需要 exploreUrl）"
                    }
                    ProbeResult(
                        index = i,
                        status = status,
                        detail = detail,
                        entry = config.probe.entry,
                        depth = config.probe.depth,
                        keyword = config.probe.keyword,
                        timeoutMs = config.probe.timeoutMs,
                        maxExplorePages = config.probe.maxExplorePages,
                    )
                } else {
                    gate.withPermit {
                        runCatching { probeEngine.probe(i, source, config.probe) }
                            .getOrElse { e -> ProbeResult(i, ProbeStatus.ERROR, detail = e.toString()) }
                    }
                }
                results[i] = result
                if (result.status == ProbeStatus.OK) ok.incrementAndGet() else failed.incrementAndGet()
                val n = done.incrementAndGet()
                if (n % config.progressEvery == 0 || n == sources.size) {
                    events.send(PipelineEvent.Progress(Phase.PROBE, n, sources.size, ok.get(), failed.get()))
                }
            }
        }
        jobs.awaitAll()
        results.mapIndexed { i, r -> i to (r ?: ProbeResult(i, ProbeStatus.SKIPPED, detail = "未返回结果")) }.toMap()
    }
}
