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
    DEDUP("去重"),
    PROBE("校验"),
    ORGANIZE("整理"),
    EXPORT("导出"),
}

sealed interface PipelineEvent {
    data class PhaseStarted(val phase: Phase, val total: Int) : PipelineEvent
    data class Progress(val phase: Phase, val done: Int, val total: Int, val ok: Int = 0, val failed: Int = 0) : PipelineEvent
    data class PhaseFinished(val phase: Phase, val elapsedMs: Long, val note: String = "") : PipelineEvent
    data class Log(val message: String) : PipelineEvent
    data class Failed(val phase: Phase, val message: String, val cause: String? = null) : PipelineEvent
}

data class PipelineConfig(
    val dedup: DedupConfig = DedupConfig(),
    val probe: ProbeOptions = ProbeOptions(),
    val organize: OrganizeConfig = OrganizeConfig(),
    val export: ExportConfig = ExportConfig(),
    /** 并发上限。超过 200 后吞吐提升有限，超时却会成批出现。 */
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
 * 这里只关心「顺序、进度、并发」；具体怎么探测由 [ProbeEngine] 提供。
 * 故意不抛异常中断全流程：单源失败只记录，不拖垮整轮。
 */
class Pipeline(private val engine: ProbeEngine?) {

    suspend fun run(
        sources: List<BookSourceDoc>,
        config: PipelineConfig = PipelineConfig(),
        emit: suspend (PipelineEvent) -> Unit = { _ -> },
    ): PipelineResult {
        val events = Channel<PipelineEvent>(Channel.BUFFERED)
        val pump = emitEvents(events, emit)

        // S1
        events.send(PipelineEvent.PhaseStarted(Phase.DEDUP, sources.size))
        var mark = TimeSource.Monotonic.markNow()
        val dedup = Deduper.run(sources, config.dedup)
        events.send(
            PipelineEvent.PhaseFinished(
                Phase.DEDUP, mark.elapsedNow().inWholeMilliseconds,
                "重复组 ${dedup.groups.size}，建议保留 ${dedup.keepCount}/${dedup.total}",
            )
        )

        // S2
        val probeResults: Map<Int, ProbeResult> = when {
            config.skipProbe || engine == null -> {
                events.send(PipelineEvent.PhaseStarted(Phase.PROBE, sources.size))
                val skipped = sources.indices.associateWith { i ->
                    ProbeResult(i, if (engine == null) ProbeStatus.SKIPPED else ProbeStatus.SKIPPED, detail = "跳过校验")
                }
                events.send(PipelineEvent.PhaseFinished(Phase.PROBE, 0, "已跳过"))
                skipped
            }
            else -> {
                events.send(PipelineEvent.PhaseStarted(Phase.PROBE, sources.size))
                mark = TimeSource.Monotonic.markNow()
                val results = probeAll(sources, config, events)
                val ok = results.values.count { it.status == ProbeStatus.OK }
                events.send(
                    PipelineEvent.PhaseFinished(
                        Phase.PROBE, mark.elapsedNow().inWholeMilliseconds,
                        "可用 $ok/${results.size}",
                    )
                )
                results
            }
        }

        // S3
        events.send(PipelineEvent.PhaseStarted(Phase.ORGANIZE, sources.size))
        mark = TimeSource.Monotonic.markNow()
        val organize = Organizer.run(sources, probeResults, config.organize)
        events.send(PipelineEvent.PhaseFinished(Phase.ORGANIZE, mark.elapsedNow().inWholeMilliseconds, "分组 ${organize.pools.size}"))

        // S4
        var exported: ExportResult? = null
        val dir = config.exportDir
        if (dir != null) {
            events.send(PipelineEvent.PhaseStarted(Phase.EXPORT, sources.size))
            mark = TimeSource.Monotonic.markNow()
            val raws = sources.map { it.raw }
            exported = if (config.exportFileName != null) {
                Exporter.write(raws, organize.sources, dir, config.exportFileName, config.export)
            } else {
                Exporter.write(raws, organize.sources, dir, Exporter.defaultFileName(), config.export)
            }
            events.send(
                PipelineEvent.PhaseFinished(
                    Phase.EXPORT, mark.elapsedNow().inWholeMilliseconds,
                    "${exported!!.count} 个源 → ${exported!!.file}",
                )
            )
        }

        events.close()
        pump.join()

        return PipelineResult(sources.size, dedup, probeResults, organize, exported)
    }

    private suspend fun probeAll(
        sources: List<BookSourceDoc>,
        config: PipelineConfig,
        events: Channel<PipelineEvent>,
    ): Map<Int, ProbeResult> = coroutineScope {
        val gate = Semaphore(config.concurrency.coerceIn(1, 2000))
        val done = AtomicInteger()
        val ok = AtomicInteger()
        val failed = AtomicInteger()
        val results = arrayOfNulls<ProbeResult>(sources.size)

        val jobs = sources.mapIndexed { i, source ->
            async(Dispatchers.Default) {
                val result = if (!source.canSearch) {
                    ProbeResult(i, ProbeStatus.NO_SEARCH_URL, detail = "searchUrl 或 ruleSearch.bookList 为空")
                } else {
                    gate.withPermit {
                        runCatching { engine!!.probe(i, source, config.probe) }
                            .getOrElse { e ->
                                ProbeResult(i, ProbeStatus.ERROR, detail = e.toString())
                            }
                    }
                }
                results[i] = result
                if (result.status == ProbeStatus.OK) ok.incrementAndGet() else failed.incrementAndGet()
                val n = done.incrementAndGet()
                if (n % config.progressEvery == 0 || n == sources.size) {
                    events.send(
                        PipelineEvent.Progress(Phase.PROBE, n, sources.size, ok.get(), failed.get())
                    )
                }
            }
        }
        jobs.awaitAll()
        results.mapIndexed { i, r ->
            i to (r ?: ProbeResult(i, ProbeStatus.SKIPPED, detail = "未返回结果"))
        }.toMap()
    }

    private fun CoroutineScope.eventsPump(
        events: Channel<PipelineEvent>,
        emit: suspend (PipelineEvent) -> Unit,
    ) = launch {
        for (e in events) emit(e)
    }

    private fun CoroutineScope.emitEvents(
        events: Channel<PipelineEvent>,
        emit: suspend (PipelineEvent) -> Unit,
    ) = launch {
        for (e in events) emit(e)
    }
}

private typealias CoroutineScope = kotlinx.coroutines.CoroutineScope
