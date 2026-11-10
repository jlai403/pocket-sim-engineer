package io.github.jlai403.pocketsimengineer.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackCursorTest {
    @Test
    fun `pre-roll maps to zero and the line maps to lap start`() {
        assertEquals(0, PlaybackCursor.telemetryMs(0, 2_000, 0, 90_000))
        assertEquals(0, PlaybackCursor.telemetryMs(2_000, 2_000, 0, 90_000))
        assertEquals(1_500, PlaybackCursor.telemetryMs(3_500, 2_000, 0, 90_000))
    }

    @Test
    fun `positive sync nudges telemetry later and negative earlier`() {
        assertEquals(3_000, PlaybackCursor.telemetryMs(3_500, 2_000, 1_500, 90_000))
        assertEquals(0, PlaybackCursor.telemetryMs(3_500, 2_000, -1_500, 90_000))
    }

    @Test
    fun `clamps to the lap duration`() {
        assertEquals(90_000, PlaybackCursor.telemetryMs(200_000, 2_000, 0, 90_000))
        assertEquals(0, PlaybackCursor.telemetryMs(-5_000, 2_000, 0, 90_000))
    }
}
