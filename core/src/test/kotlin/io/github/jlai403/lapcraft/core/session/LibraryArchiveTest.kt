package io.github.jlai403.lapcraft.core.session

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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

    @Test
    fun `partial selection of a multi-lap session uses a derived id and omits siblings`() {
        writeSession("s1", laps = listOf(1, 2))
        val out = ByteArrayOutputStream()

        LibraryArchive.export(temp, listOf(LapRef("s1", 2)), out)

        val names = entryNames(out)
        assertTrue("library/s1-l002/session.json" in names)
        assertTrue("library/s1-l002/laps/lap-002.gt7lap" in names)
        assertFalse(names.any { it.contains("lap-001") })
        assertEquals(2, SessionJsonCodec.decodeLap(readEntry(out, "library/s1-l002/lap.json")).index)
    }

    @Test
    fun `stored clip is not deflated but telemetry is`() {
        // A compressible clip payload makes the level choice visible: deflate would shrink it,
        // while level 0 keeps it at (slightly above) its original size.
        writeSession("s1", laps = listOf(1), videoBytes = ByteArray(256 * 1024) { 7 })
        writeSessionFile("s1", "laps/lap-001.gt7lap", ByteArray(256 * 1024) { 1 })
        val file = File(temp, "library.zip")
        LibraryArchive.export(temp, listOf(LapRef("s1", 1)), file.outputStream())

        ZipFile(file).use { zip ->
            val clip = zip.getEntry("library/s1/laps/lap-001.mp4")
            val telemetry = zip.getEntry("library/s1/laps/lap-001.gt7lap")
            assertEquals(ZipEntry.DEFLATED, clip.method)
            assertTrue(clip.compressedSize >= clip.size)
            assertTrue(telemetry.compressedSize < telemetry.size / 2)
        }
    }

    @Test
    fun `round trip restores sessions and telemetry`() {
        writeSession("s1", laps = listOf(1), videoBytes = ByteArray(1024) { 3 })
        writeSessionFile("s1", "laps/lap-001.gt7lap", byteArrayOf(9, 9, 9))
        val zip = File(temp, "library.zip")
        LibraryArchive.export(temp, listOf(LapRef("s1", 1)), zip.outputStream())

        val target = File(temp, "target").apply { mkdirs() }
        val report = LibraryArchive.import(target, zip.inputStream())

        assertEquals(ImportReport(laps = 1, skipped = 0, failed = 0), report)
        val restored = File(target, "sessions/s1")
        assertTrue(File(restored, "session.json").isFile)
        assertTrue(File(restored, "laps/lap-001.json").isFile)
        assertArrayEquals(byteArrayOf(9, 9, 9), File(restored, "laps/lap-001.gt7lap").readBytes())
        assertArrayEquals(ByteArray(1024) { 3 }, File(restored, "laps/lap-001.mp4").readBytes())
        assertEquals(1, SessionStore(target).summaries().size)
    }

    @Test
    fun `importing twice skips existing sessions`() {
        writeSession("s1", laps = listOf(1))
        val zip = File(temp, "library.zip")
        LibraryArchive.export(temp, listOf(LapRef("s1", 1)), zip.outputStream())
        val target = File(temp, "target").apply { mkdirs() }

        LibraryArchive.import(target, zip.inputStream())
        val second = LibraryArchive.import(target, zip.inputStream())

        assertEquals(ImportReport(laps = 0, skipped = 1, failed = 0), second)
        assertEquals(1, SessionStore(target).summaries().size)
    }

    @Test
    fun `missing referenced clip imports the lap without a replay`() {
        val zip = File(temp, "broken.zip")
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 91_000,
            startNanos = 0,
            endNanos = 0,
            sampleCount = 0,
            videoFile = "laps/lap-001.mp4",
            leadInMs = 0,
        )
        val session = SessionMetadata(
            sessionId = "s9",
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Monza",
            carCode = 3474,
            carName = "BRZ",
            rawFile = "",
            laps = listOf(lap),
        )
        writeZip(
            zip,
            mapOf(
                "library.json" to json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 0)).toByteArray(),
                "library/s9/session.json" to SessionJsonCodec.encodeSession(session).toByteArray(),
                "library/s9/lap.json" to SessionJsonCodec.encodeLap(lap).toByteArray(),
                "library/s9/laps/lap-001.gt7lap" to byteArrayOf(1),
            ),
        )

        val target = File(temp, "target").apply { mkdirs() }
        val report = LibraryArchive.import(target, zip.inputStream())

        assertEquals(ImportReport(laps = 1, skipped = 0, failed = 0), report)
        assertEquals("", SessionStore(target).lap("s9", 1)!!.videoFile)
    }

    @Test
    fun `rejects path traversal entries`() {
        val zip = File(temp, "evil.zip")
        writeZip(
            zip,
            mapOf(
                "library.json" to json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 4)).toByteArray(),
                "library/../evil/session.json" to "{}".toByteArray(),
            ),
        )
        val target = File(temp, "target").apply { mkdirs() }

        val error = assertThrows(IllegalArgumentException::class.java) {
            LibraryArchive.import(target, zip.inputStream())
        }

        assertTrue(error.message!!.contains("unsafe"))
        assertFalse(File(temp, "evil").exists())
        assertFalse(File(target, "sessions/evil").exists())
        assertTrue(File(target, "sessions").listFiles()!!.none { it.name.startsWith(".import-") })
    }

    @Test
    fun `rejects an unknown format`() {
        val zip = File(temp, "v2.zip")
        writeZip(zip, mapOf("library.json" to json.encodeToString(LibraryManifest(2, "x", 0, 0)).toByteArray()))
        val target = File(temp, "target").apply { mkdirs() }

        val error = assertThrows(IllegalArgumentException::class.java) {
            LibraryArchive.import(target, zip.inputStream())
        }

        assertTrue(error.message!!.contains("Unsupported library format 2"))
    }

    @Test
    fun `corrupt session entry is counted as failed and does not block the rest`() {
        val zip = File(temp, "mixed.zip")
        val good = SessionMetadata(
            sessionId = "good",
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Monza",
            carCode = 1,
            carName = "x",
            rawFile = "",
            laps = listOf(
                LapMetadata(
                    index = 1,
                    lapTimeMs = 90_000,
                    startNanos = 0,
                    endNanos = 0,
                    sampleCount = 0,
                    videoFile = "",
                    leadInMs = 0,
                ),
            ),
        )
        writeZip(
            zip,
            mapOf(
                "library.json" to json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 2, 0)).toByteArray(),
                "library/good/session.json" to SessionJsonCodec.encodeSession(good).toByteArray(),
                "library/good/lap.json" to SessionJsonCodec.encodeLap(good.laps.first()).toByteArray(),
                "library/good/laps/lap-001.gt7lap" to byteArrayOf(1),
                "library/bad/session.json" to "not json".toByteArray(),
                "library/bad/lap.json" to "not json".toByteArray(),
            ),
        )

        val report = LibraryArchive.import(File(temp, "target").apply { mkdirs() }, zip.inputStream())

        assertEquals(ImportReport(laps = 1, skipped = 0, failed = 1), report)
    }

    @Test
    fun `clip references that are not media are cleared`() {
        val zip = File(temp, "sneaky.zip")
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 91_000,
            startNanos = 0,
            endNanos = 0,
            sampleCount = 0,
            videoFile = "session.json",
            leadInMs = 0,
            sourceFile = "session.json",
        )
        val session = SessionMetadata(
            sessionId = "s9",
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Monza",
            carCode = 3474,
            carName = "BRZ",
            rawFile = "session.json",
            laps = listOf(lap),
        )
        writeZip(
            zip,
            mapOf(
                "library.json" to json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 0)).toByteArray(),
                "library/s9/session.json" to SessionJsonCodec.encodeSession(session).toByteArray(),
                "library/s9/lap.json" to SessionJsonCodec.encodeLap(lap).toByteArray(),
                "library/s9/laps/lap-001.gt7lap" to byteArrayOf(1),
            ),
        )

        val target = File(temp, "target").apply { mkdirs() }
        LibraryArchive.import(target, zip.inputStream())

        val stored = SessionStore(target).lap("s9", 1)!!
        assertEquals("", stored.videoFile)
        assertNull(stored.sourceFile)
        assertTrue(File(target, "sessions/s9/session.json").isFile)
    }

    @Test
    fun `lap without telemetry is counted as failed`() {
        val zip = File(temp, "no-telemetry.zip")
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 91_000,
            startNanos = 0,
            endNanos = 0,
            sampleCount = 0,
            videoFile = "",
            leadInMs = 0,
        )
        val session = SessionMetadata(
            sessionId = "s8",
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Monza",
            carCode = 3474,
            carName = "BRZ",
            rawFile = "",
            laps = listOf(lap),
        )
        writeZip(
            zip,
            mapOf(
                "library.json" to json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 0)).toByteArray(),
                "library/s8/session.json" to SessionJsonCodec.encodeSession(session).toByteArray(),
                "library/s8/lap.json" to SessionJsonCodec.encodeLap(lap).toByteArray(),
            ),
        )

        val target = File(temp, "target").apply { mkdirs() }
        val report = LibraryArchive.import(target, zip.inputStream())

        assertEquals(ImportReport(laps = 0, skipped = 0, failed = 1), report)
        assertFalse(File(target, "sessions/s8").exists())
    }

    @Test
    fun `ids that look like staging directories are rejected`() {
        val zip = File(temp, "staging-id.zip")
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 91_000,
            startNanos = 0,
            endNanos = 0,
            sampleCount = 0,
            videoFile = "",
            leadInMs = 0,
        )
        val session = SessionMetadata(
            sessionId = ".import-evil",
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Monza",
            carCode = 3474,
            carName = "BRZ",
            rawFile = "",
            laps = listOf(lap),
        )
        writeZip(
            zip,
            mapOf(
                "library.json" to json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 0)).toByteArray(),
                "library/.import-evil/session.json" to SessionJsonCodec.encodeSession(session).toByteArray(),
                "library/.import-evil/lap.json" to SessionJsonCodec.encodeLap(lap).toByteArray(),
                "library/.import-evil/laps/lap-001.gt7lap" to byteArrayOf(1),
            ),
        )

        val target = File(temp, "target").apply { mkdirs() }
        val report = LibraryArchive.import(target, zip.inputStream())

        assertEquals(ImportReport(laps = 0, skipped = 0, failed = 1), report)
        assertFalse(File(target, "sessions/.import-evil").exists())
    }

    @Test
    fun `stale staging directories are swept before import`() {
        val stale = File(temp, "target/sessions/.import-stale").apply { mkdirs() }
        File(stale, "junk.txt").writeText("x")
        val zip = File(temp, "empty.zip")
        writeZip(zip, mapOf("library.json" to json.encodeToString(LibraryManifest(1, "x", 0, 0)).toByteArray()))

        LibraryArchive.import(File(temp, "target"), zip.inputStream())

        assertFalse(stale.exists())
    }

    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun writeSessionFile(id: String, relative: String, bytes: ByteArray) {
        File(temp, "sessions/$id/$relative").writeBytes(bytes)
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
