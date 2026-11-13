package io.github.jlai403.lapcraft.core.session

/** Requested clip range in the recording's microseconds time base. */
data class ClipRange(val startUs: Long, val endUs: Long)

/** Clip start after keyframe alignment, plus the offset to the start/finish line. */
data class AlignedClip(val clipStartUs: Long, val leadInMs: Int)

object RemuxPlanner {
    const val PRE_ROLL_US = 2_000_000L
    const val POST_ROLL_US = 1_000_000L

    fun requestedRange(lapStartUs: Long, lapEndUs: Long, fileDurationUs: Long): ClipRange? {
        if (fileDurationUs <= 0 || lapEndUs <= lapStartUs) return null
        val startUs = (lapStartUs - PRE_ROLL_US).coerceAtLeast(0)
        val endUs = (lapEndUs + POST_ROLL_US).coerceAtMost(fileDurationUs)
        if (endUs <= startUs) return null
        return ClipRange(startUs, endUs)
    }

    fun alignToKeyframe(requestedStartUs: Long, keyframeUs: Long, lapStartUs: Long): AlignedClip {
        val clipStartUs = keyframeUs.coerceIn(0, requestedStartUs)
        val offsetMs = ((lapStartUs - clipStartUs) / 1_000).coerceAtLeast(0).toInt()
        return AlignedClip(clipStartUs, offsetMs)
    }
}
