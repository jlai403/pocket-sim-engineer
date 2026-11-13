package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class LapStatsTest {
    @Test
    fun `stats weight speed by time and count pedal time`() {
        val window = listOf(
            SessionFixtures.sample(0, speedMps = 10f, brake = 0, throttle = 200),
            SessionFixtures.sample(1_000_000_000L, speedMps = 20f, brake = 0, throttle = 200),
            SessionFixtures.sample(2_000_000_000L, speedMps = 30f, brake = 200, throttle = 0),
        )
        val stats = lapStats(window, lap)!!

        assertEquals(30f, stats.topSpeedMps, 0.001f)
        assertEquals(10f, stats.minSpeedMps, 0.001f)
        assertEquals(25f, stats.avgSpeedMps, 0.001f)
        assertEquals(1_000, stats.brakeTimeMs)
        assertEquals(1_000, stats.fullThrottleMs)
    }

    @Test
    fun `one sample has no stats`() {
        assertEquals(null, lapStats(listOf(SessionFixtures.sample(0)), lap))
    }

    private val lap = LapMetadata(
        index = 1,
        lapTimeMs = 2_000,
        startNanos = 0,
        endNanos = 2_000_000_000L,
        sampleCount = 3,
        videoFile = "",
        leadInMs = 0,
    )
}
