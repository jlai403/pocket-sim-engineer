package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import io.github.jlai403.pocketsimengineer.core.session.SessionWriter
import java.io.File
import android.os.Process

/**
 * Cuts per-lap clips after the recorder has finalized the raw MP4. Idempotent, so a process
 * death mid-remux is retried on the next launch. Raw video and audio are deleted only when every
 * lap produced a valid clip and `keepRaw` is off.
 */
object SessionFinalizer {
    const val IN_PROGRESS = ".in-progress"

    private val androidCutter = LapCutter { laps, raw, audio, recordingStartNanos ->
        LapRemuxer(raw.parentFile ?: error("raw video has no parent")).cut(
            laps,
            raw,
            audio,
            recordingStartNanos,
        )
    }

    fun finish(writer: SessionWriter, keepRaw: Boolean, cutter: LapCutter = androidCutter) {
        val dir = writer.sessionDir()
        File(dir, IN_PROGRESS).delete()
        val metadata = writer.metadata()
        val laps = writer.laps()
        val raw = File(dir, metadata.rawFile)

        if (laps.isEmpty()) {
            // A recording with no detected lap is still the only copy of what happened.
            writer.updateMetadata { it.copy(keepRaw = true) }
            if (raw.isFile && raw.length() > 0) {
                writer.finalize(rawDeleted = false)
            } else {
                dir.deleteRecursively()
            }
            return
        }

        val audio = metadata.audioFile?.let { File(dir, it) }
        try {
            if (raw.isFile) {
                val outcome = cutter.cut(
                    laps,
                    raw,
                    audio?.takeIf { it.isFile && it.length() > 0 },
                    metadata.recordingStartNanos,
                )
                outcome.laps.forEach(writer::updateLap)
                if (outcome.allSucceeded && !keepRaw) {
                    raw.delete()
                    audio?.delete()
                }
            }
        } finally {
            writer.updateMetadata { it.copy(keepRaw = keepRaw) }
            writer.finalize(rawDeleted = !raw.exists())
        }
    }

    fun recover(store: SessionStore, keepRaw: Boolean, cutter: LapCutter = androidCutter) {
        for (session in store.list()) {
            val marker = File(store.sessionDir(session.sessionId), IN_PROGRESS)
            if (!marker.isFile) continue
            val owner = marker.readText().trim().toIntOrNull()
            if (owner != Process.myPid()) marker.delete()
        }
        for (session in store.list()) {
            val dir = store.sessionDir(session.sessionId)
            if (File(dir, IN_PROGRESS).isFile) continue
            val raw = File(dir, session.rawFile)
            if (session.rawDeleted || !raw.isFile || session.laps.isEmpty()) continue
            if (session.laps.none { it.needsCut || it.videoFile == session.rawFile }) {
                val clipsPresent = session.laps.all { File(dir, it.videoFile).isFile }
                if (clipsPresent && !keepRaw) raw.delete()
                store.save(session.copy(rawDeleted = !raw.exists(), keepRaw = keepRaw))
                continue
            }

            val audio = session.audioFile?.let { File(dir, it) }
            runCatching {
                val outcome = cutter.cut(
                    session.laps,
                    raw,
                    audio?.takeIf { it.isFile && it.length() > 0 },
                    session.recordingStartNanos,
                )
                outcome.laps.forEach { store.updateLap(session.sessionId, it) }
                if (outcome.allSucceeded && !keepRaw) {
                    raw.delete()
                    audio?.delete()
                }
            }
            val updated = store.load(session.sessionId) ?: continue
            store.save(updated.copy(rawDeleted = !raw.exists(), keepRaw = keepRaw))
        }
    }
}
