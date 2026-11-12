package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SessionWriterTest {
    @TempDir
    lateinit var temp: File

    private fun sample(timeNanos: Long, speed: Float) = Sample(
        timeNanos = timeNanos,
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
        steering = 0f,
        currentLapTimeMs = 0,
    )

    @Test
    fun `writes session and lap files`() {
        val metadata = SessionMetadata(
            sessionId = "s1",
            createdAt = "2026-11-10T18:42:11Z",
            trackLabel = "Test Track",
            carCode = 3474,
            carName = "BRZ GT300 '21",
            rawFile = "raw.mp4",
        )
        val writer = SessionWriter(temp, metadata)
        writer.addSample(sample(0L, 40f))
        writer.addSample(sample(1_000_000_000L, 41f))
        writer.addSample(sample(2_000_000_000L, 42f))

        val lap = writer.writeLap(
            boundary = LapBoundary(
                lapNumber = 1,
                startNanos = 0L,
                endNanos = 2_000_000_000L,
                lapTimeMs = 2_000,
            ),
            videoFile = "raw.mp4",
            videoStartOffsetMs = 0,
            driverPsnId = "skino2024",
        )

        assertEquals(1, lap.index)
        assertEquals(3, lap.sampleCount)
        assertEquals("skino2024", lap.driverPsnId)
        assertEquals(
            "skino2024",
            SessionJsonCodec.decodeLap(File(temp, "laps/lap-001.json").readText()).driverPsnId,
        )
        assertTrue(File(temp, "session.json").exists())
        assertTrue(File(temp, "laps/lap-001.gt7lap").exists())
        assertTrue(File(temp, "laps/lap-001.json").exists())
        assertEquals(
            1,
            SessionJsonCodec.decodeSession(File(temp, "session.json").readText()).laps.size,
        )
        assertEquals(
            3,
            LapTelemetryCodec.decode(File(temp, "laps/lap-001.gt7lap").readBytes()).size,
        )
    }

    @Test
    fun `updateLap replaces the lap in both json files`() {
        val metadata = SessionMetadata(
            sessionId = "s1",
            createdAt = "2026-11-10T18:42:11Z",
            trackLabel = "Test Track",
            carCode = 3474,
            carName = "BRZ GT300 '21",
            rawFile = "raw.mp4",
        )
        val writer = SessionWriter(temp, metadata)
        writer.addSample(sample(0L, 40f))
        writer.addSample(sample(1_000_000_000L, 41f))
        val lap = writer.writeLap(
            boundary = LapBoundary(1, 0L, 1_000_000_000L, 1_000),
            videoFile = "raw.mp4",
            videoStartOffsetMs = 0,
        )

        writer.updateLap(
            lap.copy(videoFile = "laps/lap-001.mp4", videoStartOffsetMs = 2_000),
        )

        val fromSession = SessionJsonCodec.decodeSession(File(temp, "session.json").readText())
        val fromLap = SessionJsonCodec.decodeLap(File(temp, "laps/lap-001.json").readText())
        assertEquals("laps/lap-001.mp4", fromSession.laps.single().videoFile)
        assertEquals(2_000, fromLap.videoStartOffsetMs)
        assertEquals(listOf(lap.index), writer.laps().map { it.index })
    }

    @Test
    fun `finalize persists raw deletion state`() {
        val metadata = SessionMetadata(
            sessionId = "s1",
            createdAt = "2026-11-10T18:42:11Z",
            trackLabel = "Test Track",
            carCode = 3474,
            carName = "BRZ GT300 '21",
            rawFile = "raw.mp4",
        )
        val writer = SessionWriter(temp, metadata)
        val result = writer.finalize(rawDeleted = true)
        assertTrue(result.rawDeleted)
        assertTrue(SessionJsonCodec.decodeSession(File(temp, "session.json").readText()).rawDeleted)
    }
}
