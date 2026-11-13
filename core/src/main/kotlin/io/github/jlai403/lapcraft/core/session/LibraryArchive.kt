package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.io.AtomicFiles
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One lap in the library, addressed the same way the UI addresses rows. */
data class LapRef(val sessionId: String, val lapIndex: Int)

data class ExportReport(val laps: Int, val bytes: Long)

data class ImportReport(val laps: Int, val skipped: Int, val failed: Int)

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
    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val CLIP_PATH = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
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

    /**
     * Merges laps from an archive, never overwriting. Each lap is staged under
     * `sessions/.import-<uuid>` and moved into place only once its metadata is valid.
     */
    fun import(
        rootDir: File,
        input: InputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ImportReport {
        val sessionsDir = File(rootDir, "sessions").apply { mkdirs() }
        sweepStaging(sessionsDir)
        val staging = File(sessionsDir, ".import-${UUID.randomUUID()}")
        var manifest: LibraryManifest? = null
        var readBytes = 0L
        var imported = 0
        var skipped = 0
        var failed = 0
        try {
            ZipInputStream(BufferedInputStream(input, BUFFER)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        if (entry.name == MANIFEST) {
                            manifest = json.decodeFromString<LibraryManifest>(zip.readBytes().toString(Charsets.UTF_8))
                            readBytes += entry.size.coerceAtLeast(0)
                        } else {
                            val target = File(staging, safeEntryPath(entry.name))
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out ->
                                val buffer = ByteArray(BUFFER)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count <= 0) break
                                    out.write(buffer, 0, count)
                                    readBytes += count
                                    onProgress(readBytes, manifest?.bytes ?: 0)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val parsed = requireNotNull(manifest) { "Unsupported library format: missing $MANIFEST" }
            require(parsed.format == FORMAT) {
                "Unsupported library format ${parsed.format}; expected $FORMAT"
            }

            staging.listFiles().orEmpty().forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val id = dir.name
                if (!ID_PATTERN.matches(id)) {
                    failed++
                    dir.deleteRecursively()
                    return@forEach
                }
                if (File(sessionsDir, id).exists()) {
                    skipped++
                    dir.deleteRecursively()
                    return@forEach
                }
                if (runCatching { importStaged(dir, id, sessionsDir) }.getOrDefault(false)) {
                    imported++
                } else {
                    failed++
                    dir.deleteRecursively()
                }
            }
        } finally {
            staging.deleteRecursively()
        }
        return ImportReport(imported, skipped, failed)
    }

    private fun importStaged(dir: File, id: String, sessionsDir: File): Boolean {
        val session = SessionJsonCodec.decodeSession(File(dir, "session.json").readText())
        val incoming = SessionJsonCodec.decodeLap(File(dir, "lap.json").readText())
        require(File(dir, "laps/lap-%03d.gt7lap".format(incoming.index)).isFile) {
            "missing telemetry for lap ${incoming.index}"
        }
        val lap = incoming.copy(
            videoFile = incoming.videoFile.takeIf { it.isNotBlank() }
                ?.let { keepClip(dir, it) }
                .orEmpty(),
            sourceFile = incoming.sourceFile?.let { keepClip(dir, it) },
        )
        var stored = session.copy(sessionId = id, laps = listOf(lap))
        val raw = session.rawFile.takeIf { it.isNotBlank() }?.let { keepClip(dir, it) }
        if (raw == null) stored = stored.copy(rawFile = "", rawDeleted = true)
        AtomicFiles.writeText(File(dir, "session.json"), SessionJsonCodec.encodeSession(stored))
        AtomicFiles.writeText(
            File(dir, "laps/lap-%03d.json".format(lap.index)),
            SessionJsonCodec.encodeLap(lap),
        )
        File(dir, "lap.json").delete()
        return dir.renameTo(File(sessionsDir, id))
    }

    /** Only keeps a reference that names a real MP4 inside the staged session. */
    private fun keepClip(dir: File, relative: String): String? {
        if (!CLIP_PATH.matches(relative) || !relative.endsWith(".mp4", ignoreCase = true)) return null
        if (relative.split('/').any { it == ".." || it == "." }) return null
        return relative.takeIf { File(dir, it).isFile }
    }

    private fun sweepStaging(sessionsDir: File) {
        sessionsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(".import-") }
            ?.forEach { it.deleteRecursively() }
    }

    /** Entry names are untrusted: keep extraction inside the staging directory. */
    private fun safeEntryPath(name: String): String {
        val normalized = name.replace('\\', '/')
        val parts = normalized.split('/')
        val unsafe = normalized.startsWith("/") ||
            !normalized.startsWith("$ITEM_ROOT/") ||
            parts.any { it.isEmpty() || it == "." || it == ".." }
        require(!unsafe) { "unsafe archive entry: $name" }
        return normalized.removePrefix("$ITEM_ROOT/")
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
