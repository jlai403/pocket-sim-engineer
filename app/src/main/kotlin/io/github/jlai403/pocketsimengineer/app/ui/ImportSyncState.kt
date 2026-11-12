package io.github.jlai403.pocketsimengineer.app.ui

data class ImportSyncState(
    val durationMs: Long,
    val positionMs: Long,
    val renderedMs: Long,
    val anchorMs: Long?,
    val frameStepMs: Long,
) {
    fun stepFrame(forward: Boolean): Long {
        val delta = if (forward) frameStepMs else -frameStepMs
        return (positionMs + delta).coerceIn(0, durationMs)
    }

    fun nudge(deltaMs: Long): Long =
        (positionMs + deltaMs).coerceIn(0, durationMs)

    /** The last frame of the clip, so fine work starts at the lap end rather than the lead-in. */
    fun initialPosition(): Long = lastFrameMs(durationMs, frameStepMs)
}

internal fun lastFrameMs(durationMs: Long, frameStepMs: Long): Long {
    if (durationMs <= 0L) return 0L
    return (durationMs - frameStepMs.coerceAtLeast(1L)).coerceIn(0L, durationMs)
}
