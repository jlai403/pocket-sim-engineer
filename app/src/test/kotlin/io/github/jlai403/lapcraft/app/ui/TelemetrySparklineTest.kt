package io.github.jlai403.lapcraft.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySparklineTest {
    @Test
    fun `a window fills the sparkline width regardless of lap length`() {
        assertEquals(0f, windowTimeToX(5_000, 5_000, 15_000, 100f), 0.001f)
        assertEquals(50f, windowTimeToX(10_000, 5_000, 15_000, 100f), 0.001f)
        assertEquals(100f, windowTimeToX(15_000, 5_000, 15_000, 100f), 0.001f)
    }
}
