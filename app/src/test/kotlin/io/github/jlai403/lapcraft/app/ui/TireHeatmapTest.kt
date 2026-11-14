package io.github.jlai403.lapcraft.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TireHeatmapTest {
    @Test
    fun `tire color anchors are cold, optimal, and hot`() {
        assertEquals(AccentCyan, tireTemperatureColor(40f))
        assertEquals(AccentMint, tireTemperatureColor(90f))
        assertEquals(AccentRed, tireTemperatureColor(130f))
    }
}
