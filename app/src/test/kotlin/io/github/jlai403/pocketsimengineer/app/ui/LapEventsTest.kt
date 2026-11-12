package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LapEventsTest {
    @Test
    fun `a braking zone marks its onset once`() {
        val window = samples(
            1_000 to 0, 1_100 to 200, 1_200 to 220, 1_300 to 210, 1_400 to 0,
        )
        val brakes = lapEvents(window, lap).filter { it.type == LapEventType.Brake }
        assertEquals(listOf(LapEvent(LapEventType.Brake, 1_100)), brakes)
    }

    @Test
    fun `coasting marks where both pedals are released`() {
        val window = samples(
            1_000 to 200, 1_100 to 0, 1_200 to 0, 1_300 to 0,
        )
        assertTrue(lapEvents(window, lap).any { it.type == LapEventType.Coast && it.timeMs == 1_100 })
    }

    @Test
    fun `a steering peak marks a turn`() {
        val window = steeringSamples(1_000 to 0f, 1_100 to 0.2f, 1_200 to 0.6f, 1_300 to 0.2f)
        val turns = lapEvents(window, lap).filter { it.type == LapEventType.Turn }
        assertEquals(listOf(LapEvent(LapEventType.Turn, 1_200)), turns)
    }

    @Test
    fun `events are sorted by time`() {
        val window = steeringSamples(1_000 to 0.6f, 2_000 to 0f)
        val times = lapEvents(window, lap).map { it.timeMs }
        assertEquals(times.sorted(), times)
    }

    private fun samples(vararg brake: Pair<Int, Int>): List<Sample> = brake.map { (ms, value) ->
        SessionFixtures.sample(timeNanos = ms * 1_000_000L, brake = value, throttle = 0, speedMps = 20f)
    }

    private fun steeringSamples(vararg points: Pair<Int, Float>): List<Sample> =
        points.map { (ms, steer) ->
            SessionFixtures.sample(timeNanos = ms * 1_000_000L, brake = 0, throttle = 0, speedMps = 20f)
                .copy(steering = steer)
        }

    private val lap = LapMetadata(
        index = 1,
        lapTimeMs = 5_000,
        startNanos = 0,
        endNanos = 5_000_000_000L,
        sampleCount = 0,
        videoFile = "",
        leadInMs = 0,
    )
}
