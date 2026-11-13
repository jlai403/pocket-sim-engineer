package io.github.jlai403.pocketsimengineer.core.session

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LibraryArchiveTest {
    @TempDir
    lateinit var temp: File

    private val json = Json { encodeDefaults = true }

    @Test
    fun `exports one self-contained entry per lap`() {
        writeSession("s1", laps = listOf(1))
        val out = ByteArrayOutputStream()

        val report = LibraryArchive.export(temp, listOf(LapRef("s1", 1)), out)

        assertEquals(1, report.laps)
        val names = entryNames(out)
        assertTrue("library.json" in names)
        assertTrue("library/s1/session.json" in names)
        assertTrue("library/s1/lap.json" in names)
        assertTrue("library/s1/laps/lap-001.gt7lap" in names)
        assertTrue("library/s1/laps/lap-001.mp4" in names)
        assertFalse(names.any { it.contains(".preview") || it.endsWith(".tmp") })

        val manifest = json.decodeFromString<LibraryManifest>(readEntry(out, "library.json"))
        assertEquals(LibraryArchive.FORMAT, manifest.format)
        assertEquals(1, manifest.laps)
        assertTrue(manifest.bytes > 0)
        assertEquals(1, SessionJsonCodec.decodeSession(readEntry(out, "library/s1/session.json")).laps.size)
    }

    @Test
    fun `skips refs that do not exist`() {
        writeSession("s1", laps = listOf(1))

        val report = LibraryArchive.export(
            temp,
            listOf(LapRef("s1", 9), LapRef("nope", 1)),
            ByteArrayOutputStream(),
        )

        assertEquals(0, report.laps)
    }

    private fun writeSession(
        id: String,
        laps: List<Int>,
        videoBytes: ByteArray = byteArrayOf(1, 2, 3),
    ): File {
        val dir = File(temp, "sessions/$id").apply { mkdirs() }
        File(dir, "laps").mkdirs()
        val metadata = SessionMetadata(
            sessionId = id,
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Suzuka",
            carCode = 3474,
            carName = "BRZ GT300 '21",
            rawFile = "raw.mp4",
            laps = laps.map { index ->
                LapMetadata(
                    index = index,
                    lapTimeMs = 90_000 + index,
                    startNanos = 0L,
                    endNanos = 90_000_000_000L,
                    sampleCount = 1,
                    videoFile = "laps/lap-%03d.mp4".format(index),
                    leadInMs = 2_000,
                )
            },
        )
        File(dir, "session.json").writeText(SessionJsonCodec.encodeSession(metadata))
        for (lap in metadata.laps) {
            File(dir, "laps/lap-%03d.json".format(lap.index)).writeText(SessionJsonCodec.encodeLap(lap))
            File(dir, "laps/lap-%03d.gt7lap".format(lap.index)).writeBytes(byteArrayOf(4, 5, 6))
            File(dir, lap.videoFile).writeBytes(videoBytes)
        }
        return dir
    }

    private fun entryNames(out: ByteArrayOutputStream): List<String> =
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

    private fun readEntry(out: ByteArrayOutputStream, name: String): String =
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            val entry = generateSequence { zip.nextEntry }.first { it.name == name }
            zip.readBytes().toString(Charsets.UTF_8).also { check(entry.name == name) }
        }
}
