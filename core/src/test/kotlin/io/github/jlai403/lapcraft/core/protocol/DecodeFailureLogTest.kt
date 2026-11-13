package io.github.jlai403.lapcraft.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecodeFailureLogTest {
    @Test
    fun `records at most five distinct failures`() {
        val log = DecodeFailureLog(limit = 5)
        repeat(9) { log.record(368, "decrypt-or-magic-$it") }
        assertEquals(5, log.snapshot().size)
        assertEquals("368 decrypt-or-magic-0", log.snapshot().first())
    }

    @Test
    fun `duplicate failure is recorded once`() {
        val log = DecodeFailureLog()
        assertTrue(log.record(368, "decrypt-or-magic"))
        assertFalse(log.record(368, "decrypt-or-magic"))
    }
}
