package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.io.AtomicFiles
import io.github.jlai403.pocketsimengineer.core.model.Sample
import java.io.File
import java.time.Instant

class SessionStore(private val rootDir: File) {
    private val sessionsDir: File get() = File(rootDir, "sessions")

    fun list(): List<SessionMetadata> =
        (sessionsDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { load(it.name) }
            .sortedByDescending { parseInstant(it.createdAt) }

    fun sessionDir(sessionId: String): File = File(sessionsDir, sessionId)

    fun load(sessionId: String): SessionMetadata? {
        val file = File(sessionsDir, "$sessionId/session.json")
        if (!file.isFile) return null
        return runCatching { SessionJsonCodec.decodeSession(file.readText()) }.getOrNull()
    }

    fun save(metadata: SessionMetadata) {
        val dir = File(sessionsDir, metadata.sessionId)
        dir.mkdirs()
        AtomicFiles.writeText(File(dir, "session.json"), SessionJsonCodec.encodeSession(metadata))
    }

    fun updateLap(sessionId: String, lap: LapMetadata): SessionMetadata? {
        val metadata = load(sessionId) ?: return null
        val laps = metadata.laps.map { if (it.index == lap.index) lap else it }
        if (laps.none { it.index == lap.index }) return null
        val updated = metadata.copy(laps = laps)
        save(updated)
        AtomicFiles.writeText(
            File(sessionDir(sessionId), "laps/lap-%03d.json".format(lap.index)),
            SessionJsonCodec.encodeLap(lap),
        )
        return updated
    }

    fun rename(sessionId: String, trackLabel: String): SessionMetadata? {
        val metadata = load(sessionId) ?: return null
        val updated = metadata.copy(trackLabel = trackLabel)
        save(updated)
        return updated
    }

    /** Sync offset is per session; lap files mirror it so exports stay consistent. */
    fun setSyncOffset(sessionId: String, offsetMs: Int): SessionMetadata? {
        val metadata = load(sessionId) ?: return null
        val updatedLaps = metadata.laps.map { it.copy(syncOffsetMs = offsetMs) }
        val updated = metadata.copy(syncOffsetMs = offsetMs, laps = updatedLaps)
        save(updated)
        val lapsDir = File(sessionsDir, "$sessionId/laps")
        for (lap in updatedLaps) {
            AtomicFiles.writeText(
                File(lapsDir, "lap-%03d.json".format(lap.index)),
                SessionJsonCodec.encodeLap(lap),
            )
        }
        return updated
    }

    fun delete(sessionId: String): Boolean =
        File(sessionsDir, sessionId).deleteRecursively()

    fun readSamples(sessionId: String, lapIndex: Int): List<Sample> {
        val file = File(sessionsDir, "$sessionId/laps/lap-%03d.gt7lap".format(lapIndex))
        require(file.isFile) { "missing telemetry: ${file.path}" }
        return LapTelemetryCodec.decode(file.readBytes())
    }

    /** Flat lap rows for the library. New captures have one lap; legacy sessions may have more. */
    fun summaries(): List<LapSummary> = list().flatMap { session ->
        val dir = sessionDir(session.sessionId)
        session.laps.map { lap ->
            val replayFile = lap.videoFile
                .takeIf { it.isNotBlank() && !lap.needsCut && it != session.rawFile }
                ?.let { File(dir, it) }
                ?.takeIf { it.isFile }
            val telemetryFile = File(dir, "laps/lap-%03d.gt7lap".format(lap.index))
                .takeIf { it.isFile }
            val pendingFile = lap.sourceFile
                ?.takeIf { it.isNotBlank() }
                ?.let { File(dir, it) }
                ?.takeIf { it.isFile }
            LapSummary(
                sessionId = session.sessionId,
                lapIndex = lap.index,
                lapTimeMs = lap.lapTimeMs,
                createdAt = session.createdAt,
                trackLabel = session.trackLabel,
                carName = session.carName,
                hasReplay = replayFile != null,
                replayPending = !lap.sourceFile.isNullOrBlank(),
                driverPsnId = lap.driverPsnId,
                videoBytes = replayFile?.length() ?: 0,
                telemetryBytes = telemetryFile?.length() ?: 0,
                pendingBytes = pendingFile?.length() ?: 0,
            )
        }
    }.sortedByDescending { parseInstant(it.createdAt) }

    fun lap(sessionId: String, lapIndex: Int): LapMetadata? =
        load(sessionId)?.laps?.firstOrNull { it.index == lapIndex }

    fun setSource(sessionId: String, lapIndex: Int, sourceFile: String?): SessionMetadata? {
        val lap = lap(sessionId, lapIndex) ?: return null
        return updateLap(sessionId, lap.copy(sourceFile = sourceFile))
    }

    fun setLapDriver(sessionId: String, lapIndex: Int, psnId: String?): SessionMetadata? {
        val lap = lap(sessionId, lapIndex) ?: return null
        return updateLap(
            sessionId,
            lap.copy(driverPsnId = psnId?.trim()?.takeIf { it.isNotEmpty() }),
        )
    }

    fun attachClip(
        sessionId: String,
        lapIndex: Int,
        videoFile: String,
        leadInMs: Int,
    ): SessionMetadata? {
        val metadata = load(sessionId) ?: return null
        val lap = metadata.laps.firstOrNull { it.index == lapIndex } ?: return null
        val dir = sessionDir(sessionId)
        val source = lap.sourceFile?.let { File(dir, it) }
        val previous = lap.videoFile.takeIf { it.isNotBlank() && it != videoFile }
        val updated = updateLap(
            sessionId,
            lap.copy(
                videoFile = videoFile,
                leadInMs = leadInMs,
                syncOffsetMs = 0,
                sourceFile = null,
                needsCut = false,
            ),
        )
        if (updated == null) return null
        source?.delete()
        previous
            ?.takeIf { it != metadata.rawFile }
            ?.takeIf { old -> metadata.laps.none { other -> other.index != lapIndex && other.videoFile == old } }
            ?.let { old ->
                File(dir, old).delete()
                File(dir, old + ".tmp").delete()
                File(dir, old + ".bak").delete()
            }
        return updated
    }

    fun deleteLap(sessionId: String, lapIndex: Int): Boolean {
        val metadata = load(sessionId) ?: return false
        val lap = metadata.laps.firstOrNull { it.index == lapIndex } ?: return false
        val dir = sessionDir(sessionId)
        File(dir, "laps/lap-%03d.gt7lap".format(lapIndex)).delete()
        File(dir, "laps/lap-%03d.json".format(lapIndex)).delete()
        if (lap.videoFile.isNotBlank()) {
            val shared = metadata.laps.any {
                it.index != lapIndex && it.videoFile == lap.videoFile
            }
            if (!shared && lap.videoFile != metadata.rawFile) {
                File(dir, lap.videoFile).delete()
                File(dir, lap.videoFile + ".tmp").delete()
                File(dir, lap.videoFile + ".bak").delete()
            }
        }
        lap.sourceFile?.let { File(dir, it).delete() }
        val remaining = metadata.laps.filterNot { it.index == lapIndex }
        if (remaining.isEmpty()) {
            delete(sessionId)
        } else {
            save(metadata.copy(laps = remaining))
        }
        return true
    }

    private fun parseInstant(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
}
