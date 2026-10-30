package io.github.theeht.legadotidy.desktop.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLayoutSpecTest {

    @Test
    fun `2560px class desktop width uses expanded content and detail panel`() {
        val layout = desktopLayoutSpec(2560.dp, 1440.dp)

        assertEquals(2400.dp, layout.maxContentWidth)
        assertEquals(40.dp, layout.horizontalPadding)
        assertEquals(440.dp, layout.detailPanelWidth)
        assertEquals(20.dp, layout.panelSpacing)
        assertEquals(28.dp, layout.topPadding)
        assertEquals(32.dp, layout.bottomPadding)
    }

    @Test
    fun `125 percent scaled 2K desktop width uses medium layout`() {
        val layout = desktopLayoutSpec(2048.dp, 1152.dp)

        assertEquals(2200.dp, layout.maxContentWidth)
        assertEquals(32.dp, layout.horizontalPadding)
        assertEquals(400.dp, layout.detailPanelWidth)
        assertEquals(18.dp, layout.panelSpacing)
        assertEquals(24.dp, layout.topPadding)
        assertEquals(28.dp, layout.bottomPadding)
    }

    @Test
    fun `standard desktop width retains original panel proportions`() {
        val layout = desktopLayoutSpec(1600.dp, 900.dp)

        assertEquals(1720.dp, layout.maxContentWidth)
        assertEquals(26.dp, layout.horizontalPadding)
        assertEquals(350.dp, layout.detailPanelWidth)
        assertEquals(16.dp, layout.panelSpacing)
        assertEquals(22.dp, layout.topPadding)
        assertEquals(24.dp, layout.bottomPadding)
    }

    @Test
    fun `short desktop work area reduces vertical padding`() {
        val layout = desktopLayoutSpec(1600.dp, 768.dp)

        assertEquals(12.dp, layout.topPadding)
        assertEquals(16.dp, layout.bottomPadding)
    }
}
