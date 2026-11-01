package io.github.theeht.legadotidy.pipeline

import io.github.theeht.legadotidy.dedup.Docs
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeEntry
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageStatus
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
        val result = Pipeline(FakeEngine()).run(
            sources,
            PipelineConfig(
                probe = ProbeOptions(proxy = " http://127.0.0.1:7890 "),
            ),
        )
        assertEquals(ProbeStatus.NO_SEARCH_URL, result.probeResults.getValue(0).status)
        assertEquals("http://127.0.0.1:7890", result.probeResults.getValue(0).proxy)
    }


    @Test
    fun `错误规则组格式在流水线预检阶段被拦截`() = runBlocking {
        var calls = 0
        val engine = object : ProbeEngine {
            override suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult {
                calls++
                return ProbeResult(index, ProbeStatus.OK)
            }
        }
        val source = Docs.doc("规则组格式错误", "https://invalid.example").also {
            it.raw.add("ruleSearch", com.google.gson.JsonArray())
        }

        val result = Pipeline(engine).run(listOf(source))
        val probe = result.probeResults.getValue(0)

        assertEquals(0, calls)
        assertEquals(ProbeStatus.ERROR, probe.status)
        assertTrue(probe.detail.orEmpty().contains("ruleSearch"))
        assertEquals(
            listOf(ProbeStage.SEARCH, ProbeStage.BOOK_INFO, ProbeStage.TOC, ProbeStage.CONTENT),
            probe.stageResults.map { it.stage },
        )
    }

    @Test
    fun `缺少书源地址时记录未执行阶段且不调用引擎`() = runBlocking {
        var calls = 0
        val engine = object : ProbeEngine {
            override suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult {
                calls++
                return ProbeResult(index, ProbeStatus.OK)
            }
        }
        val source = Docs.doc("无地址", "", searchUrl = "https://example.com/search?q=%s")
        val result = Pipeline(engine).run(listOf(source))
        val probe = result.probeResults.getValue(0)

        assertEquals(0, calls)
        assertEquals(ProbeStatus.ERROR, probe.status)
        assertEquals(ProbeStage.SEARCH, probe.stageResults.first().stage)
        assertEquals(ProbeStageStatus.NOT_RUN, probe.stageResults.first().status)
        assertEquals(
            listOf(ProbeStage.SEARCH, ProbeStage.BOOK_INFO, ProbeStage.TOC, ProbeStage.CONTENT),
            probe.stageResults.map { it.stage },
        )
        assertTrue(probe.detail.orEmpty().contains("bookSourceUrl"))
    }

    @Test
    fun `发现模式允许仅有发现入口的书源进入引擎`() = runBlocking {
        val raw = Docs.doc("仅发现入口", "https://explore.example").raw.apply {
            addProperty("searchUrl", "")
            addProperty("exploreUrl", "/explore")
        }
        val source = BookSourceDoc(raw)
        assertTrue(!source.canSearch)
        assertTrue(source.canExplore)

        var receivedEntry: ProbeEntry? = null
        val engine = object : ProbeEngine {
            override suspend fun probe(
                index: Int,
                source: BookSourceDoc,
                options: ProbeOptions,
            ): ProbeResult {
                receivedEntry = options.entry
                return ProbeResult(
                    index = index,
                    status = ProbeStatus.OK,
                    entry = options.entry,
                    depth = options.depth,
                    keyword = options.keyword,
                    timeoutMs = options.timeoutMs,
                    maxExplorePages = options.maxExplorePages,
                )
            }
        }
        val result = Pipeline(engine).run(
            listOf(source),
            PipelineConfig(
                probe = ProbeOptions(entry = ProbeEntry.EXPLORE),
            ),
        )

        assertEquals(ProbeEntry.EXPLORE, receivedEntry)
        assertEquals(ProbeStatus.OK, result.probeResults.getValue(0).status)
        assertEquals(ProbeEntry.EXPLORE, result.probeResults.getValue(0).entry)
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
    fun `引擎取消异常会向上传播而不是转换成单源 ERROR`() = runBlocking {
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
    fun `默认校验选项为完整搜索链`() {
        assertEquals(io.github.theeht.legadotidy.probe.ProbeDepth.FULL_CHAIN, ProbeOptions().depth)
        assertEquals(ProbeEntry.SEARCH, ProbeOptions().entry)
    }
}
