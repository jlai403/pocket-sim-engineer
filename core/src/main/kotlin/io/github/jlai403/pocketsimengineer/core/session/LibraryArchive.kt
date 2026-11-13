package io.github.jlai403.pocketsimengineer.core.session

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One lap in the library, addressed the same way the UI addresses rows. */
data class LapRef(val sessionId: String, val lapIndex: Int)

data class ExportReport(val laps: Int, val bytes: Long)

/** Root entry naming the archive format. Import refuses anything but [LibraryArchive.FORMAT]. */
@Serializable
data class LibraryManifest(
    val format: Int,
    val exportedAt: String,
    val laps: Int,
    val bytes: Long,
)

/**
 * Lap-granular backup: one self-contained directory per lap, so a partial library
 * moves without dragging sibling laps or the session's raw capture along.
 */
object LibraryArchive {
    const val FORMAT = 1
    private const val MANIFEST = "library.json"
    private const val ITEM_ROOT = "library"
    private const val BUFFER = 64 * 1024
    private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
    private val json = Json { encodeDefaults = true }

    fun export(
        rootDir: File,
        laps: List<LapRef>,
        out: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ExportReport {
        val entries = laps.mapNotNull { resolve(rootDir, it) }
        val total = entries.sumOf { it.bytes }
        val manifest = LibraryManifest(FORMAT, Instant.now().toString(), entries.size, total)

        ZipOutputStream(BufferedOutputStream(out, BUFFER)).use { zip ->
            zip.writeText(MANIFEST, json.encodeToString(manifest))
            var written = 0L
            for (entry in entries) {
                val prefix = "$ITEM_ROOT/${entry.id}"
                zip.writeText("$prefix/session.json", entry.sessionJson)
                zip.writeText("$prefix/lap.json", entry.lapJson)
                written += entry.metadataBytes
                onProgress(written, total)
                for (file in entry.files) {
                    zip.levelFor(file.relative)
                    zip.putNextEntry(ZipEntry("$prefix/${file.relative}"))
                    file.file.inputStream().use { it.copyTo(zip, BUFFER) }
                    zip.closeEntry()
                    written += file.file.length()
                    onProgress(written, total)
                }
            }
        }
        return ExportReport(entries.size, total)
    }

    private class ArchivedFile(val relative: String, val file: File)

    private class Entry(
        val id: String,
        val sessionJson: String,
        val lapJson: String,
        val files: List<ArchivedFile>,
    ) {
        val metadataBytes: Long = (sessionJson.length + lapJson.length).toLong()
        val bytes: Long = metadataBytes + files.sumOf { it.file.length() }
    }

    private fun resolve(rootDir: File, ref: LapRef): Entry? {
        val dir = File(rootDir, "sessions/${ref.sessionId}")
        val sessionFile = File(dir, "session.json")
        if (!sessionFile.isFile) return null
        val session = runCatching { SessionJsonCodec.decodeSession(sessionFile.readText()) }.getOrNull() ?: return null
        val lap = session.laps.firstOrNull { it.index == ref.lapIndex } ?: return null
        val id = archiveId(session, lap)
        if (!ID_PATTERN.matches(id)) return null

        val relatives = buildList {
            add("laps/lap-%03d.gt7lap".format(lap.index))
            if (lap.videoFile.isNotBlank()) add(lap.videoFile)
            lap.sourceFile?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (session.rawFile.isNotBlank() && (lap.videoFile == session.rawFile || lap.sourceFile == session.rawFile)) {
                add(session.rawFile)
            }
        }.distinct()
        val files = relatives.mapNotNull { relative ->
            File(dir, relative).takeIf { it.isFile }?.let { ArchivedFile(relative, it) }
        }
        val rawIncluded = files.any { it.relative == session.rawFile }
        val stored = session.copy(
            sessionId = id,
            laps = listOf(lap),
            audioFile = null,
            keepRaw = false,
            rawFile = if (rawIncluded) session.rawFile else "",
            rawDeleted = !rawIncluded,
        )
        return Entry(
            id = id,
            sessionJson = SessionJsonCodec.encodeSession(stored),
            lapJson = SessionJsonCodec.encodeLap(lap),
            files = files,
        )
    }

    /** A session with one lap keeps its id; a legacy multi-lap session derives one per lap. */
    private fun archiveId(session: SessionMetadata, lap: LapMetadata): String =
        if (session.laps.size == 1) session.sessionId else "${session.sessionId}-l%03d".format(lap.index)

    private fun ZipOutputStream.writeText(name: String, text: String) {
        levelFor(name)
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.levelFor(name: String) {
        setLevel(if (name.endsWith(".mp4")) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
    }
}
