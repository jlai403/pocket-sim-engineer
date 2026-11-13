package io.github.jlai403.pocketsimengineer.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long an exact seek waits for its frame before the next one is issued.
 * Exact seeks re-decode from the previous keyframe, so this is deliberately
 * longer than a typical decode.
 */
internal const val EXACT_FRAME_WAIT_MS = 400L

/** Exact-seek frame wait while scrubbing a short-GOP lap clip. */
internal const val SCRUB_EXACT_WAIT_MS = 120L

/**
 * Keeps at most one seek in flight and always seeks to the newest target.
 * A fast drag produces hundreds of targets, so the player gets one per
 * rendered frame (or [exactWaitMs]) instead of a seek storm that never finishes.
 */
internal class ScrubSeeker(
    scope: CoroutineScope,
    private val exactWaitMs: Long = EXACT_FRAME_WAIT_MS,
    private val seek: (Long) -> Unit,
) {
    private val targets = Channel<Long>(Channel.CONFLATED)
    private val rendered = Channel<Unit>(Channel.CONFLATED)
    private var lastSeeked: Long? = null

    init {
        scope.launch {
            for (target in targets) {
                if (target == lastSeeked) continue
                lastSeeked = target
                // A frame queued before this seek would release the wait early.
                while (rendered.tryReceive().isSuccess) Unit
                seek(target)
                withTimeoutOrNull(exactWaitMs) { rendered.receive() }
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
