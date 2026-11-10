package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.io.AtomicFiles
import io.github.jlai403.pocketsimengineer.core.model.Sample
import java.io.File
import java.time.Duration
import java.time.Instant

data class SessionGroup(
    val carCode: Int,
    val carName: String,
    val trackLabel: String,
    val sessions: List<SessionMetadata>,
)

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

    /**
     * Consecutive sessions with the same car and track, no more than ten minutes apart,
     * form one visual group. Groups and their sessions are newest first.
     */
    fun groups(): List<SessionGroup> {
        val ascending = list().sortedBy { parseInstant(it.createdAt) }
        val groups = ArrayList<MutableList<SessionMetadata>>()
        var previous: SessionMetadata? = null
        for (session in ascending) {
            val last = previous
            val joins = last != null &&
                last.carCode == session.carCode &&
                last.trackLabel == session.trackLabel &&
                Duration.between(parseInstant(last.createdAt), parseInstant(session.createdAt))
                    .abs() <= Duration.ofMinutes(10)
            if (joins) {
                groups.last().add(session)
            } else {
                groups.add(mutableListOf(session))
            }
            previous = session
        }
        return groups.asReversed().map { members ->
            val newest = members.last()
            SessionGroup(
                carCode = newest.carCode,
                carName = newest.carName,
                trackLabel = newest.trackLabel,
                sessions = members.asReversed(),
            )
        }
    }

    private fun parseInstant(value: String): Instant =
        runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
}
