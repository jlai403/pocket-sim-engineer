package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.model.Sample
import java.io.File

class SessionWriter(
    private val rootDir: File,
    private var metadata: SessionMetadata,
) {
    private val samples = ArrayList<Sample>()
    private val laps = ArrayList<LapMetadata>()

    init {
        rootDir.mkdirs()
        File(rootDir, "laps").mkdirs()
    }

    fun sessionDir(): File = rootDir

    @Synchronized
    fun addSample(sample: Sample) {
        samples.add(sample)
    }

    @Synchronized
    fun writeLap(
        boundary: LapBoundary,
        videoFile: String,
        videoStartOffsetMs: Int,
    ): LapMetadata {
        val preRollNanos = 2_000_000_000L
        val postRollNanos = 1_000_000_000L
        val selected = samples.filter {
            it.timeNanos >= boundary.startNanos - preRollNanos &&
                it.timeNanos <= boundary.endNanos + postRollNanos
        }

        val lap = LapMetadata(
            index = boundary.lapNumber,
            lapTimeMs = boundary.lapTimeMs,
            startNanos = boundary.startNanos,
            endNanos = boundary.endNanos,
            sampleCount = selected.size,
            videoFile = videoFile,
            videoStartOffsetMs = videoStartOffsetMs,
            syncOffsetMs = metadata.syncOffsetMs,
        )

        val lapsDir = File(rootDir, "laps").apply { mkdirs() }
        File(lapsDir, "lap-%03d.gt7lap".format(lap.index)).writeBytes(LapTelemetryCodec.encode(selected))
        File(lapsDir, "lap-%03d.json".format(lap.index)).writeText(SessionJsonCodec.encodeLap(lap))

        laps.add(lap)
        metadata = metadata.copy(laps = laps.toList())
        writeSession()
        return lap
    }

    @Synchronized
    fun laps(): List<LapMetadata> = laps.toList()

    /** Replaces a lap after remux, keeping session.json and lap JSON in sync. */
    @Synchronized
    fun updateLap(lap: LapMetadata) {
        val index = laps.indexOfFirst { it.index == lap.index }
        if (index >= 0) laps[index] = lap else laps.add(lap)
        File(rootDir, "laps/lap-%03d.json".format(lap.index))
            .writeText(SessionJsonCodec.encodeLap(lap))
        metadata = metadata.copy(laps = laps.toList())
        writeSession()
    }

    @Synchronized
    fun updateMetadata(transform: (SessionMetadata) -> SessionMetadata) {
        metadata = transform(metadata)
        writeSession()
    }

    @Synchronized
    fun finalize(rawDeleted: Boolean): SessionMetadata {
        metadata = metadata.copy(rawDeleted = rawDeleted)
        writeSession()
        return metadata
    }

    private fun writeSession() {
        File(rootDir, "session.json").writeText(SessionJsonCodec.encodeSession(metadata))
    }
}
