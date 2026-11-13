package io.github.jlai403.pocketsimengineer.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a seek waits for its frame before the next one is issued. Exact seeks
 * re-decode from the previous keyframe, so this is deliberately longer than a
 * typical decode: cancelling earlier would mean no frame ever lands mid-drag.
 */
internal const val SCRUB_FRAME_WAIT_MS = 400L

/**
 * Keeps at most one exact seek in flight and always seeks to the newest target.
 * A fast drag produces hundreds of targets; the player gets one per rendered
 * frame (or [SCRUB_FRAME_WAIT_MS]) instead of a seek storm that never finishes.
 */
internal class ScrubSeeker(
    scope: CoroutineScope,
    private val frameWaitMs: Long = SCRUB_FRAME_WAIT_MS,
    private val seek: (Long) -> Unit,
) {
    private val targets = Channel<Long>(Channel.CONFLATED)
    private val rendered = Channel<Unit>(Channel.CONFLATED)
    private var lastSeeked: Long? = null

    init {
        scope.launch {
            for (target in targets) {
                if (target == lastSeeked) continue
                // A frame queued before this seek would release the wait early.
                while (rendered.tryReceive().isSuccess) Unit
                lastSeeked = target
                seek(target)
                withTimeoutOrNull(frameWaitMs) { rendered.receive() }
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
        lastSeeked = null
    }
}
