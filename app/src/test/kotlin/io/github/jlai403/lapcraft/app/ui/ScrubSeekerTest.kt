package io.github.jlai403.lapcraft.app.ui

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
        val seeker = ScrubSeeker(backgroundScope) { seeks += it }

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
    fun `a seek that never renders is released by the timeout`() = runTest {
        val seeks = mutableListOf<Long>()
        val seeker = ScrubSeeker(backgroundScope, exactWaitMs = 120L) { seeks += it }

        seeker.submit(10)
        runCurrent()
        seeker.submit(20)

        assertEquals(listOf(10L), seeks)

        advanceTimeBy(121)
        runCurrent()

        assertEquals(listOf(10L, 20L), seeks)
    }

    @Test
    fun `clear pending drops a queued target`() = runTest {
        val seeks = mutableListOf<Long>()
        val seeker = ScrubSeeker(backgroundScope) { seeks += it }

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
        val seeker = ScrubSeeker(backgroundScope) { seeks += it }

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
