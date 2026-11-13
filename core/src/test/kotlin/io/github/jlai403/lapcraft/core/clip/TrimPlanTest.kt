package io.github.jlai403.lapcraft.core.clip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrimPlanTest {
    @Test
    fun `plan rejects a clip shorter than the lap`() {
        val sync = ClipSync(videoAnchorMs = 150_000, lapTimeMs = 159_831)
        assertNull(TrimPlan.forSync(sync, sourceDurationMs = 170_000))
    }

    @Test
    fun `plan returns the exact lap range`() {
        val sync = ClipSync(videoAnchorMs = 165_100, lapTimeMs = 159_831)
        assertEquals(
            TrimRange(5_269L, 165_100L),
            TrimPlan.forSync(sync, sourceDurationMs = 170_000),
        )
    }

    @Test
    fun `plan rejects an anchor past the end of the clip`() {
        val sync = ClipSync(videoAnchorMs = 180_000, lapTimeMs = 159_831)
        assertNull(TrimPlan.forSync(sync, sourceDurationMs = 170_000))
    }
}
