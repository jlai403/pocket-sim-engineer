package io.github.jlai403.pocketsimengineer.core.playback

object PlaybackCursor {
    /**
     * Video is the master clock. A clip starts [videoStartOffsetMs] before the start/finish line;
     * [syncOffsetMs] nudges telemetry later (positive) or earlier (negative).
     */
    fun telemetryMs(
        positionMs: Long,
        videoStartOffsetMs: Int,
        syncOffsetMs: Int,
        lapTimeMs: Int,
    ): Int = (positionMs - videoStartOffsetMs + syncOffsetMs).toInt().coerceIn(0, lapTimeMs)
}
