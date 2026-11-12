package io.github.jlai403.pocketsimengineer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UiStateTest {
    @Test
    fun `short date drops the year and time`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("Nov 11", shortDateLabel("2026-11-11T12:00:00Z"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `short date falls back to the raw value`() {
        assertEquals("not-a-date", shortDateLabel("not-a-date"))
    }
}
