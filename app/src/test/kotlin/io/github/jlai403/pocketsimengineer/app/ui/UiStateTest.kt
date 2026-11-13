package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class UiStateTest {
    @Test
    fun `short date drops the year and time`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("Nov 11", shortDateLabel("2026-11-11T12:00:00Z"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `short date falls back to the raw value`() {
        assertEquals("not-a-date", shortDateLabel("not-a-date"))
    }

    @Test
    fun `sample at a sample time is that sample`() {
        val sample = samples().sampleAt(lap, 1_000)

        assertEquals(30f, sample!!.speedMps, 0.001f)
        assertEquals(4, sample.gear)
    }

    @Test
    fun `sample between samples lerps continuous values`() {
        val sample = samples().sampleAt(lap, 250)!!

        assertEquals(15f, sample.speedMps, 0.001f)
        assertEquals(50, sample.throttle)
        assertEquals(205, sample.brake)
        assertEquals(2_000f, sample.rpm, 0.001f)
        assertEquals(25f, sample.positionX, 0.001f)
        assertEquals(12.5f, sample.positionZ, 0.001f)
    }

    @Test
    fun `sample between samples lerps steering`() {
        val window = listOf(
            sample(timeNanos = 0, steering = 0f),
            sample(timeNanos = 1_000_000_000, steering = 1f),
        )

        assertEquals(0.25f, window.sampleAt(lap, 250)!!.steering, 0.001f)
    }

    @Test
    fun `gear comes from the nearest sample`() {
        assertEquals(3, samples().sampleAt(lap, 250)!!.gear)
        assertEquals(4, samples().sampleAt(lap, 750)!!.gear)
    }

    @Test
    fun `sample outside the window clamps to its ends`() {
        assertEquals(10f, samples().sampleAt(lap, -50)!!.speedMps, 0.001f)
        assertEquals(30f, samples().sampleAt(lap, 5_000)!!.speedMps, 0.001f)
    }

    @Test
    fun `sample of an empty window is null`() {
        assertNull(emptyList<Sample>().sampleAt(lap, 0))
    }

    @Test
    fun `trailing window keeps only the past`() {
        val longLap = LapMetadata(
            index = 1,
            lapTimeMs = 5_000,
            startNanos = 0,
            endNanos = 5_000_000_000L,
            sampleCount = 6,
            videoFile = "",
            leadInMs = 0,
        )
        val window = listOf(0, 1_000, 2_000, 3_000, 4_000, 5_000)
            .map { ms -> SessionFixtures.sample(timeNanos = ms * 1_000_000L) }
            .windowEndingAt(longLap, 3_000, 1_500)
        assertEquals(listOf(2_000L, 3_000L), window.map { it.timeNanos / 1_000_000 })
    }

    @Test
    fun `trailing window of an empty lap is empty`() {
        assertTrue(emptyList<Sample>().windowEndingAt(lap, 0, 5_000).isEmpty())
    }

    @Test
    fun `trailing window clamps to the lap bounds`() {
        val samples = listOf(-1_000, 0, 1_000, 2_000, 3_000)
            .map { ms -> SessionFixtures.sample(timeNanos = ms * 1_000_000L) }
        val window = samples.windowEndingAt(lap, 1_000, 5_000)
        assertEquals(listOf(0L, 1_000L), window.map { it.timeNanos / 1_000_000 })
    }

    private fun samples() = listOf(
        sample(timeNanos = 0, speedMps = 10f, brake = 255, gear = 3, rpm = 1_000f),
        sample(timeNanos = 1_000_000_000, speedMps = 30f, throttle = 200, brake = 55, gear = 4, rpm = 5_000f, x = 100f, z = 50f),
    )

    private fun sample(
        timeNanos: Long,
        speedMps: Float = 0f,
        throttle: Int = 0,
        brake: Int = 0,
        gear: Int = 0,
        rpm: Float = 0f,
        x: Float = 0f,
        z: Float = 0f,
        steering: Float = 0f,
    ) = Sample(
        timeNanos = timeNanos,
        speedMps = speedMps,
        throttle = throttle,
        brake = brake,
        gear = gear,
        rpm = rpm,
        positionX = x,
        positionY = 0f,
        positionZ = z,
        lapCount = 1,
        bestLapTimeMs = 2_000,
        lastLapTimeMs = 2_000,
        flags = 0,
        carCode = 0,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = 0,
        steering = steering,
    )

    private companion object {
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 2_000,
            startNanos = 0,
            endNanos = 2_000_000_000,
            sampleCount = 2,
            videoFile = "",
            leadInMs = 0,
        )
    }
}
