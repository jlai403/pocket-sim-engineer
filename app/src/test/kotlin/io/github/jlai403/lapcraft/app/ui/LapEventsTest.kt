package io.github.jlai403.lapcraft.app.ui

import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapMetadata
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
    fun `a light brake press marks the onset`() {
        val window = samples(
            1_000 to 0, 1_100 to 1, 1_200 to 0,
        )
        val brakes = lapEvents(window, lap).filter { it.type == LapEventType.Brake }
        assertEquals(listOf(LapEvent(LapEventType.Brake, 1_100)), brakes)
    }

    @Test
    fun `a steering corner marks apex and exit`() {
        val window = steeringSamples(
            1_000 to 0f,
            1_100 to 0.2f,
            1_200 to 0.6f,
            1_300 to 0.2f,
            1_400 to 0f,
        )
        val types = lapEvents(window, lap).map { it.type }.filter { it in TURN_TYPES }.toSet()
        assertEquals(TURN_TYPES, types)
    }

    @Test
    fun `events are sorted by time`() {
        val window = steeringSamples(1_000 to 0.6f, 2_000 to 0f)
        val times = lapEvents(window, lap).map { it.timeMs }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun `a curved path marks turns without steering`() {
        val turns = lapEvents(arcSamples(), lap).filter { it.type in TURN_TYPES }
        assertTrue(turns.any { it.type == LapEventType.Apex })
        assertTrue(turns.any { it.type == LapEventType.Exit })
    }

    @Test
    fun `a straight path has no turn`() {
        val window = (0 until 10).map { index ->
            SessionFixtures.sample(
                timeNanos = index * 100_000_000L,
                x = index * 10f,
                z = 0f,
            )
        }
        assertTrue(lapEvents(window, lap).none { it.type in TURN_TYPES })
    }

    private fun arcSamples(): List<Sample> {
        val steps = listOf(0.01f, 0.02f, 0.04f, 0.08f, 0.12f, 0.08f, 0.04f, 0.02f, 0.01f, 0.01f)
        var angle = 0f
        var x = 0f
        var z = 0f
        return (0..12).map { index ->
            if (index > 0) {
                angle += steps[(index - 1).coerceAtMost(steps.lastIndex)]
                x += 20f * kotlin.math.cos(angle)
                z += 20f * kotlin.math.sin(angle)
            }
            SessionFixtures.sample(timeNanos = index * 100_000_000L, x = x, z = z)
        }
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

    private companion object {
        val TURN_TYPES = setOf(
            LapEventType.Apex,
            LapEventType.Exit,
        )
    }
}
