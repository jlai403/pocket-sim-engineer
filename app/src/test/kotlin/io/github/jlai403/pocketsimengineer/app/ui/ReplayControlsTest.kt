package io.github.jlai403.pocketsimengineer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayControlsTest {
    @Test
    fun `drag distance carries whole frames`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(2, steps.framesFor(25f))
        assertEquals(1, steps.framesFor(5f))
    }

    @Test
    fun `dragging back cancels pending progress`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(-2, steps.framesFor(-25f))
        assertEquals(-1, steps.framesFor(-5f))
    }

    @Test
    fun `reset drops a partial frame`() {
        val steps = FrameSteps(pxPerFrame = 10f)

        assertEquals(0, steps.framesFor(9f))
        steps.reset()
        assertEquals(0, steps.framesFor(9f))
    }

    @Test
    fun `ruler labels show tenths`() {
        assertEquals("0:00.0", formatRulerLabel(0))
        assertEquals("0:32.5", formatRulerLabel(32_500))
        assertEquals("1:30.0", formatRulerLabel(90_000))
    }
}
