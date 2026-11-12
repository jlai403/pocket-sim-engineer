package io.github.jlai403.pocketsimengineer.core.clip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClipSyncTest {
    @Test
    fun `anchor at the line gives a lap-length trim`() {
        val sync = ClipSync(videoAnchorMs = 165_100, lapTimeMs = 159_831)
        assertEquals(5_269L, sync.trimInMs)
        assertEquals(165_100L, sync.trimOutMs)
        assertEquals(0, sync.telemetryMsAt(5_269L))
        assertEquals(159_831, sync.telemetryMsAt(165_100L))
        assertEquals(5_269L, sync.videoMsAt(0))
    }

    @Test
    fun `clip shorter than the lap is blocked`() {
        val sync = ClipSync(videoAnchorMs = 150_000, lapTimeMs = 159_831)
        assertEquals(SyncCheck.ClipTooShort(missingMs = 9_831L), SyncValidator.check(sync, 170_000, 0f))
    }

    @Test
    fun `anchor beyond the clip is blocked`() {
        val sync = ClipSync(videoAnchorMs = 180_000, lapTimeMs = 159_831)
        assertEquals(SyncCheck.ClipTooShort(missingMs = 10_000L), SyncValidator.check(sync, 170_000, 0f))
    }

    @Test
    fun `start frame away from the line is blocked`() {
        val sync = ClipSync(videoAnchorMs = 165_100, lapTimeMs = 159_831)
        assertEquals(SyncCheck.StartNotAtLine(41f), SyncValidator.check(sync, 170_000, 41f))
        assertEquals(SyncCheck.Ok, SyncValidator.check(sync, 170_000, 0.4f))
        assertEquals(SyncCheck.Ok, SyncValidator.check(sync, 170_000, 99.6f))
    }
}
