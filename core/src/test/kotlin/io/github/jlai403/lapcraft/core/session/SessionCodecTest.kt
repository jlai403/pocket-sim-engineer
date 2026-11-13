package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCodecTest {
    private fun sample(t: Long, speed: Float) = Sample(
        timeNanos = t,
        speedMps = speed,
        throttle = 200,
        brake = 10,
        gear = 4,
        rpm = 7000f,
        positionX = 1f,
        positionY = 2f,
        positionZ = 3f,
        lapCount = 2,
        bestLapTimeMs = 88_000,
        lastLapTimeMs = 90_000,
        flags = 1,
        carCode = 3474,
        fuel = 45.5f,
        tyreTempFL = 80f,
        tyreTempFR = 81f,
        tyreTempRL = 82f,
        tyreTempRR = 83f,
        packetId = 99,
        steering = 0.1f,
        currentLapTimeMs = 12_345,
    )

    @Test
    fun `session json round trips`() {
        val metadata = SessionMetadata(
            schemaVersion = 1,
            sessionId = "2026-11-10T18-42-11Z",
            createdAt = "2026-11-10T18:42:11Z",
            trackLabel = "Suzuka",
            carCode = 3474,
            carName = "BRZ GT300 '21",
            syncOffsetMs = 0,
            rawFile = "raw.mp4",
            rawDeleted = false,
            keepRaw = false,
            laps = emptyList(),
        )
        assertEquals(metadata, SessionJsonCodec.decodeSession(SessionJsonCodec.encodeSession(metadata)))
    }

    @Test
    fun `lap json round trips`() {
        val lap = LapMetadata(
            schemaVersion = 1,
            index = 1,
            lapTimeMs = 90_000,
            startNanos = 0L,
            endNanos = 90_000_000_000L,
            sampleCount = 5400,
            videoFile = "lap-001.mp4",
            leadInMs = 2000,
            syncOffsetMs = 0,
        )
        assertEquals(lap, SessionJsonCodec.decodeLap(SessionJsonCodec.encodeLap(lap)))
    }

    @Test
    fun `lap json keeps the legacy lead-in key`() {
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 90_000,
            startNanos = 0L,
            endNanos = 90_000_000_000L,
            sampleCount = 5400,
            videoFile = "lap-001.mp4",
            leadInMs = 2000,
        )

        assertTrue("videoStartOffsetMs" in SessionJsonCodec.encodeLap(lap))
    }

    @Test
    fun `lap telemetry round trips`() {
        val samples = listOf(sample(0L, 42.5f), sample(16_666_667L, 43.0f))
        val bytes = LapTelemetryCodec.encode(samples)
        assertEquals("G7LP", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(2, LapTelemetryCodec.decode(bytes).size)
        assertEquals(samples, LapTelemetryCodec.decode(bytes))
    }
}
