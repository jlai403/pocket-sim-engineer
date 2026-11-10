package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import java.io.File

data class RemuxOutcome(
    val laps: List<LapMetadata>,
    val allSucceeded: Boolean,
)

/** Cuts lap clips from a finalized raw recording. Kept as an interface so finalization is testable. */
fun interface LapCutter {
    fun cut(
        laps: List<LapMetadata>,
        rawVideo: File,
        audio: File?,
        recordingStartNanos: Long,
    ): RemuxOutcome
}
