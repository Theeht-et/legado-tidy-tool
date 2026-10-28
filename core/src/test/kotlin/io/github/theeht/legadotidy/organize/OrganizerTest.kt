package io.github.theeht.legadotidy.organize

import io.github.theeht.legadotidy.dedup.Docs
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class OrganizerTest {

    @Test
    fun `有用户分组就保留，没有就按类型命名`() {
        val a = Docs.doc("甲", "https://a.com", extra = mapOf("bookSourceGroup" to "小说"))
        val b = Docs.doc("乙", "https://b.com")
        val report = Organizer.run(listOf(a, b), emptyMap())
        assertEquals("小说", report.sources[0].group)
        assertEquals("文本", report.sources[1].group)
        assertEquals(2, report.pools.size)
    }

    @Test
    fun `可用源排在超时与错误之前`() {
        val sources = listOf(
            Docs.doc("坏", "https://a.com"),
            Docs.doc("好", "https://b.com"),
            Docs.doc("慢", "https://c.com"),
        )
        val results = mapOf(
            0 to ProbeResult(0, ProbeStatus.HTTP_ERROR, httpCode = 404),
            1 to ProbeResult(1, ProbeStatus.OK, bookCount = 12),
            2 to ProbeResult(2, ProbeStatus.TIMEOUT),
        )
        val report = Organizer.run(sources, results)
        val order = report.sources.map { it.index }
        assertEquals(listOf(1, 2, 0), order)
        assertEquals(listOf(0, 1, 2), report.sources.map { it.rank })
    }
}
