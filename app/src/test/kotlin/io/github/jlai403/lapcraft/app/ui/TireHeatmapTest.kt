package io.github.jlai403.lapcraft.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TireHeatmapTest {
    @Test
    fun `tire color anchors are cold, optimal, and hot`() {
        assertEquals(AccentBlue, tireTemperatureColor(40f))
        assertEquals(AccentGreen, tireTemperatureColor(90f))
        assertEquals(Color(0xFFFF6B6B), tireTemperatureColor(130f))
    }
}
