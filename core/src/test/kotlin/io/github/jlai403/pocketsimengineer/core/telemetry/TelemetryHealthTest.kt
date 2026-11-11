package io.github.jlai403.pocketsimengineer.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryHealthTest {
    @Test
    fun `healthy stream is connected with a full packet rate`() {
        val health = TelemetryHealth.evaluate(true, 59.8, 12)
        assertTrue(health.healthy)
        assertEquals("Streaming", health.reason)
    }

    @Test
    fun `disconnected stream is waiting`() {
        assertFalse(TelemetryHealth.evaluate(false, 0.0, -1).healthy)
    }

    @Test
    fun `stale packets are not healthy even if connected`() {
        assertFalse(TelemetryHealth.evaluate(true, 60.0, 2_500).healthy)
        assertFalse(TelemetryHealth.evaluate(true, 60.0, -1).healthy)
    }

    @Test
    fun `low packet rate is not healthy`() {
        assertFalse(TelemetryHealth.evaluate(true, 20.0, 50).healthy)
        assertTrue(TelemetryHealth.evaluate(true, 50.0, 50).healthy)
    }
}
