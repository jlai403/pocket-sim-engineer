package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.session.SessionJsonCodec
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionFinalizerTest {

    private fun writer(root: File): SessionWriter {
        val metadata = SessionMetadata(
            sessionId = "s1",
            createdAt = "2026-11-10T10:00:00Z",
            trackLabel = "Suzuka",
            recordingStartNanos = 1_000_000_000L,
            carCode = 3474,
            carName = "BRZ GT300 '21",
            rawFile = "raw.mp4",
            audioFile = "audio.m4a",
        )
        return SessionWriter(root, metadata)
    }

    private fun addLap(writer: SessionWriter) {
        writer.writeLap(
            boundary = LapBoundary(1, 1_000_000_000L, 91_000_000_000L, 90_000),
            videoFile = "raw.mp4",
            videoStartOffsetMs = 0,
        )
    }

    private fun successCutter(): LapCutter = LapCutter { laps, _, _, _ ->
        RemuxOutcome(
            laps.map { it.copy(videoFile = "laps/lap-%03d.mp4".format(it.index), needsCut = false) },
            allSucceeded = true,
        )
    }

    @Test
    fun `successful cut deletes raw and audio and records rawDeleted`() {
        val dir = Files.createTempDirectory("finalizer").toFile()
        val writer = writer(dir)
        addLap(writer)
        File(dir, "raw.mp4").writeText("raw")
        File(dir, "audio.m4a").writeText("audio")

        SessionFinalizer.finish(writer, keepRaw = false, cutter = successCutter())

        assertFalse(File(dir, "raw.mp4").exists())
        assertFalse(File(dir, "audio.m4a").exists())
        val metadata = SessionJsonCodec.decodeSession(File(dir, "session.json").readText())
        assertTrue(metadata.rawDeleted)
        assertEquals("laps/lap-001.mp4", metadata.laps.single().videoFile)
    }

    @Test
    fun `failed cut keeps raw and audio`() {
        val dir = Files.createTempDirectory("finalizer").toFile()
        val writer = writer(dir)
        addLap(writer)
        File(dir, "raw.mp4").writeText("raw")
        File(dir, "audio.m4a").writeText("audio")

        val failure = LapCutter { laps, _, _, _ ->
            RemuxOutcome(laps.map { it.copy(needsCut = true) }, allSucceeded = false)
        }
        SessionFinalizer.finish(writer, keepRaw = false, cutter = failure)

        assertTrue(File(dir, "raw.mp4").isFile)
        assertTrue(File(dir, "audio.m4a").isFile)
        val metadata = SessionJsonCodec.decodeSession(File(dir, "session.json").readText())
        assertFalse(metadata.rawDeleted)
        assertTrue(metadata.laps.single().needsCut)
    }

    @Test
    fun `keep raw preserves files after a successful cut`() {
        val dir = Files.createTempDirectory("finalizer").toFile()
        val writer = writer(dir)
        addLap(writer)
        File(dir, "raw.mp4").writeText("raw")
        File(dir, "audio.m4a").writeText("audio")

        SessionFinalizer.finish(writer, keepRaw = true, cutter = successCutter())

        assertTrue(File(dir, "raw.mp4").isFile)
        assertFalse(SessionJsonCodec.decodeSession(File(dir, "session.json").readText()).rawDeleted)
    }

    @Test
    fun `empty session keeps the raw recording`() {
        val dir = Files.createTempDirectory("finalizer").toFile()
        val writer = writer(dir)
        File(dir, "raw.mp4").writeText("raw")

        SessionFinalizer.finish(writer, keepRaw = false, cutter = successCutter())

        assertTrue(dir.isDirectory)
        assertTrue(File(dir, "raw.mp4").isFile)
        assertTrue(File(dir, "session.json").isFile)
    }

    @Test
    fun `session without laps or raw is removed`() {
        val dir = Files.createTempDirectory("finalizer").toFile()
        val writer = writer(dir)

        SessionFinalizer.finish(writer, keepRaw = false, cutter = successCutter())

        assertFalse(dir.exists())
    }

    @Test
    fun `empty session with keep raw is preserved`() {
        val dir = Files.createTempDirectory("finalizer").toFile()
        val writer = writer(dir)
        File(dir, "raw.mp4").writeText("raw")

        SessionFinalizer.finish(writer, keepRaw = true, cutter = successCutter())

        assertTrue(dir.isDirectory)
        assertTrue(File(dir, "raw.mp4").isFile)
        assertTrue(File(dir, "session.json").isFile)
    }
}
