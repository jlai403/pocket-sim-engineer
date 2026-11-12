package io.github.jlai403.pocketsimengineer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSyncStateTest {
    @Test
    fun `frame step moves by one frame and clamps`() {
        val state = ImportSyncState(
            durationMs = 10_000,
            positionMs = 0,
            renderedMs = 0,
            anchorMs = null,
            frameStepMs = 33,
        )
        assertEquals(33L, state.stepFrame(forward = true))
        assertEquals(0L, state.stepFrame(forward = false))
    }

    @Test
    fun `nudge never leaves the clip`() {
        val state = ImportSyncState(
            durationMs = 10_000,
            positionMs = 5_000,
            renderedMs = 5_000,
            anchorMs = null,
            frameStepMs = 33,
        )
        assertEquals(10_000L, state.nudge(10_000))
        assertEquals(0L, state.nudge(-10_000))
    }
}
