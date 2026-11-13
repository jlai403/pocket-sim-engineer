package io.github.jlai403.pocketsimengineer.app.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrubSeekerTest {
    @Test
    fun `a burst seeks the newest target after the frame lands`() = runTest {
        val seeks = mutableListOf<Long>()
        val seeker = ScrubSeeker(backgroundScope, waitForFrame = true) { seeks += it }

        seeker.submit(10)
        runCurrent()
        seeker.submit(20)
        seeker.submit(30)

        assertEquals(listOf(10L), seeks)

        seeker.onFrameRendered()
        runCurrent()

        assertEquals(listOf(10L, 30L), seeks)
    }

    @Test
    fun `keyframe seeks are paced by the interval`() = runTest {
        val seeks = mutableListOf<Long>()
        val seeker = ScrubSeeker(backgroundScope, seekIntervalMs = 33L) { seeks += it }

        seeker.submit(10)
        runCurrent()
        seeker.submit(20)
        seeker.submit(30)

        assertEquals(listOf(10L), seeks)

        advanceTimeBy(34)
        runCurrent()

        assertEquals(listOf(10L, 30L), seeks)
    }

    @Test
    fun `clear pending drops a queued target`() = runTest {
        val seeks = mutableListOf<Long>()
        val seeker = ScrubSeeker(backgroundScope, waitForFrame = true) { seeks += it }

        seeker.submit(10)
        runCurrent()
        seeker.submit(20)
        seeker.clearPending()
        seeker.onFrameRendered()
        runCurrent()

        assertEquals(listOf(10L), seeks)
    }

    @Test
    fun `repeating the same target seeks once`() = runTest {
        val seeks = mutableListOf<Long>()
        val seeker = ScrubSeeker(backgroundScope, waitForFrame = true) { seeks += it }

        seeker.submit(10)
        runCurrent()
        seeker.onFrameRendered()
        runCurrent()
        seeker.submit(10)
        runCurrent()
        seeker.onFrameRendered()
        runCurrent()

        assertEquals(listOf(10L), seeks)
    }
}
