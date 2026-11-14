package io.github.jlai403.lapcraft.core.clip

import io.github.jlai403.lapcraft.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TrackProgressTest {
    private fun sample(x: Double, z: Double, timeNanos: Long) = Sample(
        timeNanos = timeNanos,
        speedMps = 0f,
        throttle = 0,
        brake = 0,
        gear = 1,
        rpm = 0f,
        positionX = x.toFloat(),
        positionY = 0f,
        positionZ = z.toFloat(),
        lapCount = 1,
        bestLapTimeMs = 0,
        lastLapTimeMs = 0,
        flags = 0,
        carCode = 0,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = 0,
    )

    /** 400 m square, one sample per 1% of the lap. */
    private fun squareLap(): List<Sample> {
        val side = 100.0
        return (0..100).map { index ->
            val distance = index / 100.0 * 4 * side
            val (x, z) = when {
                distance <= side -> distance to 0.0
                distance <= 2 * side -> side to distance - side
                distance <= 3 * side -> side - (distance - 2 * side) to side
                else -> 0.0 to side - (distance - 3 * side)
            }
            sample(x, z, timeNanos = index * 1_000_000_000L)
        }
    }

    @Test
    fun `percent follows the distance around the lap`() {
        val samples = squareLap()
        val start = samples.first().timeNanos
        assertEquals(0f, TrackProgress.percentAt(samples, start, 0), 0.5f)
        assertEquals(50f, TrackProgress.percentAt(samples, start, 50_000), 1f)
        assertEquals(100f, TrackProgress.percentAt(samples, start, 100_000), 0.5f)
    }

    @Test
    fun `percent interpolates between sample times`() {
        val samples = listOf(
            sample(0.0, 0.0, 0),
            sample(100.0, 0.0, 1_000_000_000L),
        )
        val start = samples.first().timeNanos

        assertEquals(25f, TrackProgress.percentAt(samples, start, 250), 0.01f)
        assertEquals(75f, TrackProgress.percentAt(samples, start, 750), 0.01f)
    }

    @Test
    fun `large teleports do not count as distance`() {
        val samples = listOf(
            sample(0.0, 0.0, 0),
            sample(0.0, 0.0, 1_000_000_000L),
            sample(10.0, 0.0, 2_000_000_000L),
            sample(5_000.0, 0.0, 3_000_000_000L),
        )
        val percent = TrackProgress.percentAt(samples, 0, 2_000)
        assertEquals(100f, percent, 1f)
    }

    @Test
    fun `timeAtPercent inverts percentAt`() {
        val samples = squareLap()
        val start = samples.first().timeNanos
        for (ms in listOf(0, 25_000, 50_000, 75_000, 100_000)) {
            val percent = TrackProgress.percentAt(samples, start, ms)
            val back = TrackProgress.timeAtPercent(samples, start, percent, 100_000)
            assertTrue(abs(back - ms) <= 40, "ms=$ms percent=$percent back=$back")
        }
    }

    @Test
    fun `timeAtPercent clamps to the lap ends`() {
        val samples = squareLap()
        val start = samples.first().timeNanos
        assertEquals(0, TrackProgress.timeAtPercent(samples, start, -10f, 100_000))
        assertEquals(100_000, TrackProgress.timeAtPercent(samples, start, 110f, 100_000))
    }

    @Test
    fun `timeAtPercent honors a lap time longer than the samples`() {
        val samples = listOf(
            sample(0.0, 0.0, 0),
            sample(100.0, 0.0, 1_000_000_000L),
        )
        assertEquals(500, TrackProgress.timeAtPercent(samples, 0, 50f, 90_000))
        assertEquals(90_000, TrackProgress.timeAtPercent(samples, 0, 100f, 90_000))
    }

    @Test
    fun `percentAt is continuous across sample times`() {
        val samples = squareLap()
        val start = samples.first().timeNanos
        var previous = 0f
        for (ms in 0..100_000 step 16) {
            val percent = TrackProgress.percentAt(samples, start, ms)
            assertTrue(percent >= previous, "percent went backwards at $ms ms")
            assertTrue(percent - previous < 1f, "percent jumped ${percent - previous} at $ms ms")
            previous = percent
        }
    }

    @Test
    fun `a lap with no movement has no table`() {
        val samples = listOf(
            sample(10.0, 10.0, 0),
            sample(10.0, 10.0, 1_000_000_000L),
        )
        assertEquals(null, TrackProgress.table(samples, 0))
        assertEquals(0f, TrackProgress.percentAt(samples, 0, 500))
    }
}
