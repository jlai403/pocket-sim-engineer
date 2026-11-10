package io.github.jlai403.pocketsimengineer.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RemuxPlannerTest {
    @Test
    fun `requests two seconds before and one second after the lap`() {
        val range = RemuxPlanner.requestedRange(
            lapStartUs = 10_000_000,
            lapEndUs = 100_000_000,
            fileDurationUs = 200_000_000,
        )!!
        assertEquals(8_000_000, range.startUs)
        assertEquals(101_000_000, range.endUs)
    }

    @Test
    fun `clamps the pre-roll to the start of the file`() {
        val range = RemuxPlanner.requestedRange(
            lapStartUs = 1_000_000,
            lapEndUs = 80_000_000,
            fileDurationUs = 200_000_000,
        )!!
        assertEquals(0, range.startUs)
        assertEquals(81_000_000, range.endUs)
    }

    @Test
    fun `clamps the post-roll to the end of the file`() {
        val range = RemuxPlanner.requestedRange(
            lapStartUs = 10_000_000,
            lapEndUs = 200_000_000,
            fileDurationUs = 200_000_000,
        )!!
        assertEquals(200_000_000, range.endUs)
    }

    @Test
    fun `returns null for an empty or inverted lap`() {
        assertNull(
            RemuxPlanner.requestedRange(
                lapStartUs = 100_000_000,
                lapEndUs = 100_000_000,
                fileDurationUs = 200_000_000,
            ),
        )
        assertNull(
            RemuxPlanner.requestedRange(
                lapStartUs = 0,
                lapEndUs = 90_000_000,
                fileDurationUs = 0,
            ),
        )
    }

    @Test
    fun `aligns the clip to the preceding keyframe and grows the offset`() {
        val aligned = RemuxPlanner.alignToKeyframe(
            requestedStartUs = 8_000_000,
            keyframeUs = 6_500_000,
            lapStartUs = 10_000_000,
        )
        assertEquals(6_500_000, aligned.clipStartUs)
        assertEquals(3_500, aligned.videoStartOffsetMs)
    }

    @Test
    fun `keeps a two second offset when the request already starts on a keyframe`() {
        val aligned = RemuxPlanner.alignToKeyframe(
            requestedStartUs = 8_000_000,
            keyframeUs = 8_000_000,
            lapStartUs = 10_000_000,
        )
        assertEquals(2_000, aligned.videoStartOffsetMs)
    }

    @Test
    fun `never reports a negative offset when the keyframe is after the request`() {
        val aligned = RemuxPlanner.alignToKeyframe(
            requestedStartUs = 8_000_000,
            keyframeUs = 9_000_000,
            lapStartUs = 10_000_000,
        )
        assertEquals(8_000_000, aligned.clipStartUs)
        assertEquals(2_000, aligned.videoStartOffsetMs)
    }
}
