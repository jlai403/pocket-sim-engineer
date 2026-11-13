package io.github.jlai403.lapcraft.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryStatsTest {
    @Test
    fun `counts packets inside the one second window`() {
        val stats = TelemetryStats(windowNanos = 1_000_000_000)
        repeat(60) { stats.onPacket(0L) }
        assertEquals(60.0, stats.packetsPerSecond(0L), 0.001)
        assertEquals(60L, stats.acceptedPackets())
        assertEquals(0.0, stats.packetsPerSecond(2_000_000_000L), 0.001)
    }

    @Test
    fun `counts decode errors`() {
        val stats = TelemetryStats()
        repeat(3) { stats.onDecodeError() }
        assertEquals(3L, stats.decodeErrors())
    }
}
