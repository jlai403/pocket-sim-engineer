package io.github.jlai403.pocketsimengineer.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubSeekerTest {
    private val seeks = mutableListOf<Long>()
    private val seeker = ScrubSeeker(CoroutineScope(Dispatchers.Unconfined)) { seeks += it }

    @Test
    fun `a burst seeks the newest target after the frame lands`() {
        seeker.submit(10)
        seeker.submit(20)
        seeker.submit(30)

        assertEquals(listOf(10L), seeks)

        seeker.onFrameRendered()

        assertEquals(listOf(10L, 30L), seeks)
    }

    @Test
    fun `clear pending drops a queued target`() {
        seeker.submit(10)
        seeker.submit(20)
        seeker.clearPending()

        seeker.onFrameRendered()

        assertEquals(listOf(10L), seeks)
    }
}
