package io.github.theeht.legadotidy.engine

import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegadoProbeEngineTest {

    @Test
    fun \`默认六项校验开关与 Legado CheckSource 一致\`() {
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
    fun \`默认关键词和单源总超时与 Legado CheckSource 一致\`() {
        val options = ProbeOptions()

        assertEquals("我的", options.keyword)
        assertEquals(180_000L, options.timeoutMs)
    }

    @Test
    fun \`无法检测域名不等同于校验失败\`() {
        assertTrue(ProbeStatus.UNCHECKABLE.label.contains("无法检测"))
    }
}
