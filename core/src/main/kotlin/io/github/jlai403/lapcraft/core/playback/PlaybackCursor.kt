package io.github.jlai403.lapcraft.core.playback

object PlaybackCursor {
    /**
     * Video is the master clock. A clip starts [leadInMs] before the start/finish line;
     * [syncOffsetMs] nudges telemetry later (positive) or earlier (negative).
     */
    fun telemetryMs(
        positionMs: Long,
        leadInMs: Int,
        syncOffsetMs: Int,
        lapTimeMs: Int,
    ): Int = (positionMs - leadInMs + syncOffsetMs).toInt().coerceIn(0, lapTimeMs)

    /** Inverse of [telemetryMs]: the clip position for a lap time. */
    fun clipMs(lapMs: Int, leadInMs: Int, syncOffsetMs: Int): Long =
        (lapMs + leadInMs - syncOffsetMs).toLong()
}
