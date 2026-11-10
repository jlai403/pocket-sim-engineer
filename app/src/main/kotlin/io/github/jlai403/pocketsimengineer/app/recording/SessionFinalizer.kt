package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import io.github.jlai403.pocketsimengineer.core.session.SessionWriter
import java.io.File

/**
 * Cuts per-lap clips after the recorder has finalized the raw MP4. Idempotent, so a process
 * death mid-remux is retried on the next launch.
 */
object SessionFinalizer {

    fun finish(writer: SessionWriter, keepRaw: Boolean) {
        val dir = writer.sessionDir()
        if (writer.laps().isEmpty()) {
            dir.deleteRecursively()
            return
        }
        cutLaps(dir, writer.laps(), keepRaw, writer::updateLap)
        writer.finalize(rawDeleted = !File(dir, "raw.mp4").exists())
    }

    fun recover(store: SessionStore, keepRaw: Boolean) {
        for (session in store.list()) {
            val dir = store.sessionDir(session.sessionId)
            val raw = File(dir, session.rawFile)
            if (session.rawDeleted || !raw.isFile || session.laps.isEmpty()) continue
            if (session.laps.none { it.needsCut || it.videoFile == session.rawFile }) continue
            cutLaps(dir, session.laps, keepRaw) { store.updateLap(session.sessionId, it) }
            val updated = store.load(session.sessionId) ?: continue
            store.save(updated.copy(rawDeleted = !raw.exists()))
        }
    }

    private fun cutLaps(
        sessionDir: File,
        laps: List<LapMetadata>,
        keepRaw: Boolean,
        updateLap: (LapMetadata) -> Unit,
    ) {
        val raw = File(sessionDir, "raw.mp4")
        if (!raw.isFile) return
        val audio = File(sessionDir, "audio.m4a").takeIf { it.isFile }
        val result = LapRemuxer(sessionDir).remux(laps, raw, audio)
        result.laps.forEach(updateLap)
        if (result.allSucceeded && !keepRaw) {
            raw.delete()
            audio?.delete()
        }
    }
}
