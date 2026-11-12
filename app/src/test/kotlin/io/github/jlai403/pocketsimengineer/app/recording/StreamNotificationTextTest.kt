package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.app.service.TelemetryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamNotificationTextTest {
    private fun state(
        connected: Boolean = true,
        packetsPerSecond: Double = 59.8,
        decodeErrors: Long = 0,
        lastPacketAgeMs: Long = 12,
        lastDecodeFailure: String = "",
    ) = TelemetryUiState(
        connected = connected,
        packetsPerSecond = packetsPerSecond,
        decodeErrors = decodeErrors,
        lastDecodeFailure = lastDecodeFailure,
        lastPacketAgeMs = lastPacketAgeMs,
        lapCount = 3,
        speedMps = 50f,
        gear = 4,
        throttle = 200,
        brake = 0,
        rpm = 7_000f,
        packetId = 84_213,
        carCode = 3474,
        flags = 1,
        lastLapTimeMs = 90_456,
    )

    @Test
    fun `summary shows rate and errors while streaming`() {
        assertEquals(
            "Recording lap 3 · 60 pps · 0 err",
            StreamNotificationText.summary("Recording lap 3", state()),
        )
        assertEquals(
            "Armed · waiting for GT7",
            StreamNotificationText.summary("Armed", state(connected = false)),
        )
    }

    @Test
    fun `stream summary is explicit while waiting`() {
        assertEquals("GT7 stream · waiting", StreamNotificationText.streamSummary(state(connected = false)))
        assertEquals("GT7 stream · 60 pps", StreamNotificationText.streamSummary(state()))
    }

    @Test
    fun `details expose the decoded fields`() {
        val details = StreamNotificationText.details(state(), "Lap 2 complete · 1:30.456")

        assertTrue(details.contains("Packets/s: 59.8 · errors: 0"))
        assertTrue(details.contains("Last packet: 12 ms ago"))
        assertTrue(details.contains("Packet ID 84213"))
        assertTrue(details.contains("car 3474"))
        assertTrue(details.contains("flags 0x0001 (on track)"))
        assertTrue(details.contains("Lap 3 · last 1:30.456"))
        assertTrue(details.endsWith("Last event: Lap 2 complete · 1:30.456"))
    }

    @Test
    fun `details name the last decode failure`() {
        val details = StreamNotificationText.details(
            state(lastDecodeFailure = "368 decrypt-or-magic"),
            null,
        )
        assertTrue(details.contains("Last decode failure: 368 decrypt-or-magic"))
    }

    @Test
    fun `missing packets and lap times are explicit`() {        val details = StreamNotificationText.details(
            state(connected = false, lastPacketAgeMs = -1, packetsPerSecond = 0.0),
            null,
        )
        assertTrue(details.contains("Last packet: none yet"))
        assertTrue(details.contains("Lap 3 · last 1:30.456"))
        assertEquals("-", StreamNotificationText.lapTime(0))
    }
}
