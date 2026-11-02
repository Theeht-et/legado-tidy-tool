package io.github.theeht.legadotidy.engine

import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.TocEmptyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegadoProbeEngineTest {

    @Test
    fun defaultCheckOptionsMatchUpstream() {
        val options = ProbeOptions()

        assertFalse(options.checkDomain)
        assertTrue(options.checkSearch)
        assertTrue(options.checkDiscovery)
        assertTrue(options.checkInfo)
        assertTrue(options.checkCategory)
        assertTrue(options.checkContent)
        assertTrue(options.writeErrorComment)
    }

    @Test
    fun defaultKeywordAndTimeoutMatchUpstream() {
        val options = ProbeOptions()

        assertEquals("我的", options.keyword)
        assertEquals(180_000L, options.timeoutMs)
    }

    @Test
    fun invalidGlobalProxyIsRejectedBeforeAValidationBatchStarts() {
        assertTrue(
            LegadoProbeEngine.validateProxyConfiguration("not a proxy")
                ?.startsWith("代理配置无效") == true,
        )
        assertNull(LegadoProbeEngine.validateProxyConfiguration(null))
        assertNull(LegadoProbeEngine.validateProxyConfiguration("   "))
        assertNull(LegadoProbeEngine.validateProxyConfiguration("http://127.0.0.1:7890"))
    }

    @Test
    fun uncheckableDomainIsNotAValidationFailure() {
        assertTrue(ProbeStatus.UNCHECKABLE.label.contains("无法检测"))
    }
    @Test
    fun chapterSelectionSkipsUpstreamVolumeHeadingsAndUsesSecondReadableChapter() {
        val first = BookChapter(title = "第一章", url = "/chapter/1")
        val second = BookChapter(title = "第二章", url = "/chapter/2")
        val selected = LegadoProbeEngine().selectCheckSourceChapter(
            listOf(
                BookChapter(title = "第一卷", url = "第一卷", isVolume = true),
                first,
                BookChapter(title = "第二卷", url = "第二卷", isVolume = true),
                second,
            ),
        )

        assertSame(first, selected.chapter)
        assertEquals(second.url, selected.nextChapterUrl)
        assertEquals(4, selected.chapterCount)
    }

    @Test
    fun chapterSelectionTreatsVolumeWithIndependentUrlAsReadableLikeUpstream() {
        val volume = BookChapter(
            title = "第一卷",
            url = "/chapter/volume-1",
            isVolume = true,
        )
        val selected = LegadoProbeEngine().selectCheckSourceChapter(listOf(volume))

        assertSame(volume, selected.chapter)
        assertEquals(volume.url, selected.nextChapterUrl)
    }

    @Test
    fun chapterSelectionRejectsDirectoryContainingOnlyVolumeHeadings() {
        assertFailsWith<TocEmptyException> {
            LegadoProbeEngine().selectCheckSourceChapter(
                listOf(
                    BookChapter(title = "第一卷", url = "第一卷", isVolume = true),
                    BookChapter(title = "第二卷", url = "第二卷", isVolume = true),
                ),
            )
        }
    }

}
