package io.github.jlai403.lapcraft.core.lap

import io.github.jlai403.lapcraft.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LapDetectorTest {
    private fun sample(
        timeNanos: Long,
        lapCount: Int,
        lastLapTimeMs: Int = 0,
        packetId: Int = 0,
    ) = Sample(
        timeNanos = timeNanos,
        speedMps = 0f,
        throttle = 0,
        brake = 0,
        gear = 1,
        rpm = 0f,
        positionX = 0f,
        positionY = 0f,
        positionZ = 0f,
        lapCount = lapCount,
        bestLapTimeMs = 0,
        lastLapTimeMs = lastLapTimeMs,
        flags = 1,
        carCode = 3474,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = packetId,
        steering = 0f,
        currentLapTimeMs = 0,
    )

    @Test
    fun `ignores the out lap`() {
        val detector = LapDetector()
        assertNull(detector.onSample(sample(0, 0, packetId = 1)))
        assertNull(detector.onSample(sample(1_000_000_000, 1, lastLapTimeMs = 0, packetId = 2)))
    }

    @Test
    fun `emits the first completed lap`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1, packetId = 1))
        val boundary = detector.onSample(sample(90_000_000_000, 2, lastLapTimeMs = 90_000, packetId = 2))
        assertEquals(1, boundary?.lapNumber)
        assertEquals(90_000, boundary?.lapTimeMs)
        assertEquals(0L, boundary?.startNanos)
        assertEquals(90_000_000_000L, boundary?.endNanos)
    }

    @Test
    fun `ignores duplicate packet ids`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1, packetId = 10))
        detector.onSample(sample(1_000_000_000, 1, packetId = 10))
        val boundary = detector.onSample(sample(90_000_000_000, 2, 90_000, packetId = 11))
        assertEquals(1, boundary?.lapNumber)
    }

    @Test
    fun `drops an implausible lap time and still resets lap start`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1, packetId = 1))
        assertNull(
            detector.onSample(sample(90_000_000_000, 2, lastLapTimeMs = 2_000, packetId = 2)),
        )
    }

    @Test
    fun `drops a lap that started before the recording began`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1, packetId = 1))
        assertNull(
            detector.onSample(sample(20_000_000_000, 2, lastLapTimeMs = 90_000, packetId = 2)),
        )
    }

    @Test
    fun `computes lap time when last lap time is missing`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1, packetId = 1))
        val boundary = detector.onSample(sample(75_000_000_000, 2, lastLapTimeMs = 0, packetId = 2))
        assertEquals(75_000, boundary?.lapTimeMs)
    }

    @Test
    fun `lap start is unobserved until a crossing happens`() {
        val detector = LapDetector()
        detector.onSample(sample(30_000_000_000, 1, packetId = 1))
        assertFalse(detector.currentLapStartObserved)

        detector.onSample(sample(90_000_000_000, 2, lastLapTimeMs = 60_000, packetId = 2))
        assertTrue(detector.currentLapStartObserved)
    }

    @Test
    fun `lap reset makes the next start unobserved`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 3, packetId = 1))
        detector.onSample(sample(10_000_000_000, 1, packetId = 2))
        assertFalse(detector.currentLapStartObserved)
    }
}
