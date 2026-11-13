package io.github.jlai403.pocketsimengineer.app.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayPreviewCacheTest {
    @Test
    fun `nearest frame is at or before the target`() {
        val times = listOf(0L, 500L, 1_000L, 1_500L)

        assertEquals(0, nearestPreviewFrame(times, 0))
        assertEquals(0, nearestPreviewFrame(times, 499))
        assertEquals(1, nearestPreviewFrame(times, 500))
        assertEquals(1, nearestPreviewFrame(times, 999))
        assertEquals(3, nearestPreviewFrame(times, 9_999))
    }

    @Test
    fun `an empty preview has no frame`() {
        assertEquals(-1, nearestPreviewFrame(emptyList(), 1_000))
    }

    @Test
    fun `a negative time has no frame`() {
        assertEquals(-1, nearestPreviewFrame(listOf(0L, 500L), -1))
    }

    @Test
    fun `before the first keyframe resolves to the first frame`() {
        assertEquals(0, nearestPreviewFrame(listOf(120L, 620L), 0))
    }
}
