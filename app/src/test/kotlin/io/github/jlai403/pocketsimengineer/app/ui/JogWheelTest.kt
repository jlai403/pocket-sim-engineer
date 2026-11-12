package io.github.jlai403.pocketsimengineer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class JogWheelTest {
    @Test
    fun `one turn equals one second of frames`() {
        assertEquals(30, JogAccumulator(framesPerTurn = 30).framesFor(1f))
    }

    @Test
    fun `small turns accumulate into whole frames`() {
        val accumulator = JogAccumulator(framesPerTurn = 30)
        repeat(3) { assertEquals(0, accumulator.framesFor(0.01f)) }
        assertEquals(1, accumulator.framesFor(0.01f))
    }

    @Test
    fun `turning back cancels pending progress`() {
        val accumulator = JogAccumulator(framesPerTurn = 30)
        assertEquals(18, accumulator.framesFor(0.6f))
        assertEquals(-18, accumulator.framesFor(-0.6f))
    }

    @Test
    fun `wraparound takes the shortest signed angle`() {
        assertEquals(-0.1f, wrapRadians(2f * PI.toFloat() - 0.1f), 0.0001f)
        assertEquals(0.1f, wrapRadians(-2f * PI.toFloat() + 0.1f), 0.0001f)
    }
}
