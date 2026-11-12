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
}
