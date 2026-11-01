package io.github.theeht.legadotidy.probe

import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeStagePlanTest {
    @Test
    fun `搜索完整链按上游顺序列出所有阶段`() {
        assertEquals(
            listOf(ProbeStage.SEARCH, ProbeStage.BOOK_INFO, ProbeStage.TOC, ProbeStage.CONTENT),
            expectedProbeStages(ProbeEntry.SEARCH, ProbeDepth.FULL_CHAIN),
        )
    }

    @Test
    fun `发现完整链包含分类解析发现分页及阅读阶段`() {
        assertEquals(
            listOf(
                ProbeStage.EXPLORE_CATEGORIES,
                ProbeStage.EXPLORE,
                ProbeStage.BOOK_INFO,
                ProbeStage.TOC,
                ProbeStage.CONTENT,
            ),
            expectedProbeStages(ProbeEntry.EXPLORE, ProbeDepth.FULL_CHAIN),
        )
    }

    @Test
    fun `记录已失败阶段后只补充尚未执行的阶段`() {
        assertEquals(
            listOf(ProbeStage.BOOK_INFO, ProbeStage.TOC, ProbeStage.CONTENT),
            notRunProbeStages(
                ProbeEntry.SEARCH,
                ProbeDepth.FULL_CHAIN,
                "搜索失败",
                alreadyRecorded = setOf(ProbeStage.SEARCH),
            ).map { it.stage },
        )
    }
}
