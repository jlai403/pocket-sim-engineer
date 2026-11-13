package io.github.jlai403.pocketsimengineer.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a keyframe seek gets before the next one is issued. Nearest-keyframe
 * seeks flush the codec but do not decode a GOP, so they finish far quicker
 * than an exact seek; two display frames is enough to keep the video moving.
 */
internal const val SCRUB_SEEK_INTERVAL_MS = 33L

/**
 * How long an exact seek waits for its frame before the next one is issued.
 * Exact seeks re-decode from the previous keyframe, so this is deliberately
 * longer than a typical decode.
 */
internal const val EXACT_FRAME_WAIT_MS = 400L

/**
 * Keeps at most one seek in flight and always seeks to the newest target.
 * A fast drag produces hundreds of targets; with [waitForFrame] the player
 * gets one per rendered frame (or [EXACT_FRAME_WAIT_MS]), otherwise one every
 * [seekIntervalMs] instead of a seek storm that never finishes.
 */
internal class ScrubSeeker(
    scope: CoroutineScope,
    private val seekIntervalMs: Long = SCRUB_SEEK_INTERVAL_MS,
    private val waitForFrame: Boolean = false,
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
                if (waitForFrame) {
                    // A frame queued before this seek would release the wait early.
                    while (rendered.tryReceive().isSuccess) Unit
                    seek(target)
                    withTimeoutOrNull(EXACT_FRAME_WAIT_MS) { rendered.receive() }
                } else {
                    seek(target)
                    delay(seekIntervalMs)
                }
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
