package io.github.jlai403.pocketsimengineer.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long a seek waits for its frame before the next one is issued. */
internal const val SCRUB_FRAME_WAIT_MS = 80L

/**
 * Keeps at most one exact seek in flight and always seeks to the newest target.
 * A fast drag produces hundreds of targets; the player gets one per rendered
 * frame (or [SCRUB_FRAME_WAIT_MS]) instead of a seek storm that never finishes.
 */
internal class ScrubSeeker(
    scope: CoroutineScope,
    private val seek: (Long) -> Unit,
) {
    private val targets = Channel<Long>(Channel.CONFLATED)
    private val rendered = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (target in targets) {
                // A frame queued before this seek would release the wait early.
                while (rendered.tryReceive().isSuccess) Unit
                seek(target)
                withTimeoutOrNull(SCRUB_FRAME_WAIT_MS) { rendered.receive() }
            }
        }
    }

    fun submit(targetMs: Long) {
        targets.trySend(targetMs)
    }

    /** Called from the player's frame listener, possibly off the main thread. */
    fun onFrameRendered() {
        rendered.trySend(Unit)
    }

    /** Drops queued targets, for when another control takes over the playhead. */
    fun clearPending() {
        while (targets.tryReceive().isSuccess) Unit
    }
}
