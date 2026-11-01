package io.github.theeht.legadotidy.pipeline

import io.github.theeht.legadotidy.dedup.Docs
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelineTest {

    private class FakeEngine(private val failing: Set<Int> = emptySet()) : ProbeEngine {
        override suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult =
            if (index in failing) {
                ProbeResult(index, ProbeStatus.TIMEOUT, options = options)
            } else {
                ProbeResult(index, ProbeStatus.OK, bookCount = 10, elapsedMs = 5, options = options)
            }
    }

    @Test
    fun fourPhasesEmitEventsAndExportFile(@TempDir dir: Path) = runBlocking {
        val sources = List(5) { Docs.doc("源$it", "https://s$it.com") }
        val events = mutableListOf<PipelineEvent>()
        val result = Pipeline(FakeEngine()).run(
            sources,
            PipelineConfig(exportDir = dir, concurrency = 2, progressEvery = 2),
        ) { events.add(it) }

        assertEquals(5, result.total)
        assertEquals(5, result.okCount)
        assertNotNull(result.exported)
        assertTrue(Files.exists(result.exported!!.file))

        val started = events.filterIsInstance<PipelineEvent.PhaseStarted>().map { it.phase }
        assertEquals(listOf(Phase.PROBE, Phase.DEDUP, Phase.ORGANIZE, Phase.EXPORT), started)
        assertTrue(events.any { it is PipelineEvent.Progress && it.done == 5 })
    }

    @Test
    fun oneSourceFailureDoesNotAbortTheWholePipeline(@TempDir dir: Path) = runBlocking {
        val sources = List(3) { Docs.doc("源$it", "https://s$it.com") }
        val result = Pipeline(FakeEngine(failing = setOf(1))).run(
            sources, PipelineConfig(exportDir = dir, concurrency = 3),
        )
        assertEquals(2, result.okCount)
        assertEquals(ProbeStatus.TIMEOUT, result.probeResults.getValue(1).status)
    }

    @Test
    fun pipelineDoesNotPreRejectSourcesWithoutSearchUrl() = runBlocking {
        val source = BookSourceDoc(
            Docs.doc("无搜索", "https://a.com", searchUrl = "", bookList = "").raw,
        )
        assertTrue(!source.canSearch)

        var receivedSource: BookSourceDoc? = null
        var receivedOptions: ProbeOptions? = null
        val engine = object : ProbeEngine {
            override suspend fun probe(
                index: Int,
                source: BookSourceDoc,
                options: ProbeOptions,
            ): ProbeResult {
                receivedSource = source
                receivedOptions = options
                return ProbeResult(index, ProbeStatus.OK, options = options)
            }
        }
        val options = ProbeOptions(
            checkSearch = false,
            checkDiscovery = true,
            proxy = " http://127.0.0.1:7890 ",
        )
        val result = Pipeline(engine).run(listOf(source), PipelineConfig(probe = options))

        assertEquals(source, receivedSource)
        assertEquals(false, receivedOptions?.checkSearch)
        assertEquals(true, receivedOptions?.checkDiscovery)
        assertEquals(ProbeStatus.OK, result.probeResults.getValue(0).status)
        assertEquals(" http://127.0.0.1:7890 ", result.probeResults.getValue(0).proxy)
    }

    @Test
    fun allDuplicateCandidatesAreProbedBeforeDeduplication() = runBlocking {
        val sources = listOf(
            Docs.doc("校验通过", "https://same.example"),
            Docs.doc("校验失败", "https://same.example"),
        )
        val events = mutableListOf<PipelineEvent>()

        val result = Pipeline(FakeEngine(failing = setOf(1))).run(
            sources,
            PipelineConfig(concurrency = 2),
        ) { events.add(it) }

        val started = events.filterIsInstance<PipelineEvent.PhaseStarted>().map { it.phase }
        assertEquals(listOf(Phase.PROBE, Phase.DEDUP, Phase.ORGANIZE), started)
        assertEquals(ProbeStatus.OK, result.probeResults.getValue(0).status)
        assertEquals(ProbeStatus.TIMEOUT, result.probeResults.getValue(1).status)
        assertEquals(0, result.dedup.groups.single().keep)
    }

    @Test
    fun cancellationFromEnginePropagatesInsteadOfBecomingSingleSourceError() = runBlocking {
        val engine = object : ProbeEngine {
            override suspend fun probe(
                index: Int,
                source: BookSourceDoc,
                options: ProbeOptions,
            ): ProbeResult {
                throw CancellationException("cancelled by engine")
            }
        }
        val failure = runCatching {
            Pipeline(engine).run(listOf(Docs.doc("cancel", "https://cancel.test")))
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun defaultProbeOptionsMatchLegadoCheckSourceDefaults() {
        val options = ProbeOptions()
        assertEquals("我的", options.keyword)
        assertEquals(180_000L, options.timeoutMs)
        assertEquals(false, options.checkDomain)
        assertEquals(true, options.checkSearch)
        assertEquals(true, options.checkDiscovery)
        assertEquals(true, options.checkInfo)
        assertEquals(true, options.checkCategory)
        assertEquals(true, options.checkContent)
        assertEquals(true, options.writeErrorComment)
    }
}
