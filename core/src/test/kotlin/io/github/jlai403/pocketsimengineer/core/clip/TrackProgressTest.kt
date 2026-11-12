package io.github.jlai403.pocketsimengineer.core.clip

import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
