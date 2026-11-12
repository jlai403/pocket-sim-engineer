package io.github.jlai403.pocketsimengineer.app.capture

import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class LapSaverTest {
    private fun sample(atMs: Long, lapCount: Int = 1) = Sample(
        timeNanos = atMs * 1_000_000,
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
        lastLapTimeMs = 0,
        flags = 1,
        carCode = 3474,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = 0,
    )

    private fun boundary(startMs: Long, endMs: Long, lap: Int = 1) = LapBoundary(
        lapNumber = lap,
        startNanos = startMs * 1_000_000,
        endNanos = endMs * 1_000_000,
        lapTimeMs = (endMs - startMs).toInt(),
    )

    @Test
    fun `saves one session with one lap and no video`() {
        val root = Files.createTempDirectory("laps").toFile()
        val saver = LapSaver(
            root,
            idFactory = { "lap-test" },
            clock = { Instant.parse("2026-11-11T10:00:00Z") },
        )
        val samples = (0..120).map { sample(atMs = it * 1_000L) }
        val metadata = saver.save(boundary(startMs = 0, endMs = 120_000), samples)

        assertEquals("lap-test", metadata.sessionId)
        assertEquals(1, metadata.laps.size)
        assertTrue(File(root, "lap-test/session.json").isFile)
        assertTrue(File(root, "lap-test/laps/lap-001.gt7lap").isFile)
        assertTrue(metadata.laps.single().videoFile.isEmpty())
    }

    @Test
    fun `track identification sees only the lap samples`() {
        val root = Files.createTempDirectory("laps").toFile()
        var seen: List<Sample>? = null
        val saver = LapSaver(
            root,
            idFactory = { "lap-test" },
            trackName = {
                seen = it
                "Suzuka"
            },
        )
        val samples = (-20..120).map { sample(atMs = it * 1_000L) }
        val metadata = saver.save(boundary(startMs = 0, endMs = 120_000), samples)

        assertEquals("Suzuka", metadata.trackLabel)
        assertTrue(seen!!.isNotEmpty())
        assertTrue(seen!!.none { it.timeNanos < 0 })
    }
}
