package io.github.theeht.legadotidy.pipeline

import io.github.theeht.legadotidy.dedup.Docs
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
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
            if (index in failing) ProbeResult(index, ProbeStatus.TIMEOUT)
            else ProbeResult(index, ProbeStatus.OK, bookCount = 10, elapsedMs = 5)
    }

    @Test
    fun `四个阶段都有事件并导出文件`(@TempDir dir: Path) = runBlocking {
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
    fun `单源失败不影响整轮`(@TempDir dir: Path) = runBlocking {
        val sources = List(3) { Docs.doc("源$it", "https://s$it.com") }
        val result = Pipeline(FakeEngine(failing = setOf(1))).run(
            sources, PipelineConfig(exportDir = dir, concurrency = 3),
        )
        assertEquals(2, result.okCount)
        assertEquals(ProbeStatus.TIMEOUT, result.probeResults.getValue(1).status)
    }

    @Test
    fun `没有搜索规则的源直接标为不可校验`() = runBlocking {
        val sources = listOf(Docs.doc("无搜索", "https://a.com", searchUrl = "", bookList = ""))
        val result = Pipeline(FakeEngine()).run(sources, PipelineConfig(skipProbe = false))
        assertEquals(ProbeStatus.NO_SEARCH_URL, result.probeResults.getValue(0).status)
    }


    @Test
    fun `先校验全部重复候选再使用校验结果推荐保留项`() = runBlocking {
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
    fun `跳过校验时全部标为 SKIPPED`() = runBlocking {
        val sources = List(2) { Docs.doc("源$it", "https://s$it.com") }
        val result = Pipeline(null).run(sources, PipelineConfig(skipProbe = true))
        assertTrue(result.probeResults.values.all { it.status == ProbeStatus.SKIPPED })
    }
}
