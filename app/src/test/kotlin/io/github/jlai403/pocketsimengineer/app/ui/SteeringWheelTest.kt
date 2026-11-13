package io.github.jlai403.pocketsimengineer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SteeringWheelTest {
    @Test
    fun `left steering turns the wheel counter-clockwise`() {
        assertEquals(-90f, steeringWheelDegrees(0.5f), 0.001f)
        assertEquals(90f, steeringWheelDegrees(-0.5f), 0.001f)
        assertEquals(0f, steeringWheelDegrees(0f), 0.001f)
    }

    @Test
    fun `steering is clamped to a half turn`() {
        assertEquals(-180f, steeringWheelDegrees(4f), 0.001f)
        assertEquals(180f, steeringWheelDegrees(-4f), 0.001f)
    }

    @Test
    fun `description names the direction and angle`() {
        assertEquals("Steering wheel centred", steeringDescription(0f))
        assertEquals("Steering wheel 90 degrees left", steeringDescription(0.5f))
        assertEquals("Steering wheel 45 degrees right", steeringDescription(-0.25f))
    }
}
