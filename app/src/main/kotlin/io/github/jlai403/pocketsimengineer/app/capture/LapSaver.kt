package io.github.jlai403.pocketsimengineer.app.capture

import io.github.jlai403.pocketsimengineer.core.capture.LapSink
import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionWriter
import java.io.File
import java.time.Instant
import java.util.UUID

/** Writes a captured lap as a one-lap session. Video is attached later. */
class LapSaver(
    private val sessionsDir: File,
    private val idFactory: () -> String = { "lap-" + UUID.randomUUID() },
    private val clock: () -> Instant = { Instant.now() },
    private val trackName: (List<Sample>) -> String? = { null },
) : LapSink {
    override fun save(boundary: LapBoundary, samples: List<Sample>): SessionMetadata {
        val id = idFactory()
        val carCode = samples.lastOrNull()?.carCode ?: 0
        val lapSamples = samples.filter { it.timeNanos in boundary.startNanos..boundary.endNanos }
        val metadata = SessionMetadata(
            sessionId = id,
            createdAt = clock().toString(),
            trackLabel = trackName(lapSamples) ?: "Unknown",
            carCode = carCode,
            carName = "Car #$carCode",
            rawFile = "",
            rawDeleted = true,
        )
        val writer = SessionWriter(File(sessionsDir, id), metadata)
        samples.forEach(writer::addSample)
        writer.writeLap(boundary, videoFile = "", videoStartOffsetMs = 0)
        writer.finalize(rawDeleted = true)
        return writer.metadata()
    }
}
