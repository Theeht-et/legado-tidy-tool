package io.github.theeht.legadotidy.engine

import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun uncheckableDomainIsNotAValidationFailure() {
        assertTrue(ProbeStatus.UNCHECKABLE.label.contains("无法检测"))
    }
}
