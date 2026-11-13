package io.github.jlai403.lapcraft.core.capture

import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LapSampleBufferTest {
    private fun sample(atMs: Long) = Sample(
        timeNanos = atMs * 1_000_000,
        speedMps = 0f,
        throttle = 0,
        brake = 0,
        gear = 1,
        rpm = 0f,
        positionX = 0f,
        positionY = 0f,
        positionZ = 0f,
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

    private fun boundary(startMs: Long, endMs: Long) = LapBoundary(
        lapNumber = 1,
        startNanos = startMs * 1_000_000,
        endNanos = endMs * 1_000_000,
        lapTimeMs = (endMs - startMs).toInt(),
    )

    @Test
    fun `window includes pre-roll and stops at the line`() {
        val buffer = LapSampleBuffer()
        (0..10).forEach { buffer.add(sample(it * 1_000L)) }

        val window = buffer.samplesFor(boundary(startMs = 5_000, endMs = 8_000), preRollNanos = 2_000_000_000L)
        assertEquals(listOf(3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 8_000L), window.map { it.timeNanos / 1_000_000 })
    }

    @Test
    fun `covers requires a sample before the line`() {
        val ordered = LapSampleBuffer()
        ordered.add(sample(0))
        ordered.add(sample(1_000))
        assertTrue(ordered.covers(boundary(startMs = 1_000, endMs = 2_000)))

        val fromLine = LapSampleBuffer()
        fromLine.add(sample(1_000))
        assertFalse(fromLine.covers(boundary(startMs = 1_000, endMs = 2_000)))
    }

    @Test
    fun `trim drops older samples`() {
        val buffer = LapSampleBuffer()
        buffer.add(sample(0))
        buffer.add(sample(1_000))
        buffer.trimBefore(1_000_000_000L)
        assertEquals(listOf(1_000L), buffer.samplesFor(boundary(0, 2_000)).map { it.timeNanos / 1_000_000 })
    }

    @Test
    fun `keeps only the last fifteen minutes`() {
        val buffer = LapSampleBuffer()
        buffer.add(sample(0))
        buffer.add(sample(16 * 60_000L))
        assertEquals(1, buffer.samplesFor(boundary(0, 16 * 60_000L), preRollNanos = 0).size)
    }
}
