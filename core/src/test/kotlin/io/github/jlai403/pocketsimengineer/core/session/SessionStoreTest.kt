package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SessionStoreTest {
    @TempDir
    lateinit var temp: File

    private fun writeSession(
        id: String,
        createdAt: String,
        carCode: Int = 3474,
        trackLabel: String = "Suzuka",
    ): File {
        val dir = File(temp, "sessions/$id")
        dir.mkdirs()
        File(dir, "laps").mkdirs()
        val metadata = SessionMetadata(
            sessionId = id,
            createdAt = createdAt,
            trackLabel = trackLabel,
            carCode = carCode,
            carName = "BRZ GT300 '21",
            rawFile = "raw.mp4",
            laps = listOf(
                LapMetadata(
                    index = 1,
                    lapTimeMs = 90_000,
                    startNanos = 0L,
                    endNanos = 90_000_000_000L,
                    sampleCount = 1,
                    videoFile = "laps/lap-001.mp4",
                    videoStartOffsetMs = 2_000,
                ),
            ),
        )
        File(dir, "session.json").writeText(SessionJsonCodec.encodeSession(metadata))
        File(dir, "laps/lap-001.json").writeText(
            SessionJsonCodec.encodeLap(metadata.laps.first()),
        )
        return dir
    }

    @Test
    fun `lists sessions newest first`() {
        writeSession("s1", "2026-11-10T10:00:00Z")
        writeSession("s2", "2026-11-10T11:00:00Z")
        val store = SessionStore(temp)
        assertEquals(listOf("s2", "s1"), store.list().map { it.sessionId })
    }

    @Test
    fun `ignores directories without session json`() {
        File(temp, "sessions/junk").mkdirs()
        writeSession("s1", "2026-11-10T10:00:00Z")
        assertEquals(listOf("s1"), SessionStore(temp).list().map { it.sessionId })
    }

    @Test
    fun `loads and deletes a session`() {
        val dir = writeSession("s1", "2026-11-10T10:00:00Z")
        val store = SessionStore(temp)
        assertEquals("Suzuka", store.load("s1")?.trackLabel)
        assertTrue(store.delete("s1"))
        assertFalse(dir.exists())
        assertNull(store.load("s1"))
    }

    @Test
    fun `renames the track label`() {
        writeSession("s1", "2026-11-10T10:00:00Z")
        val store = SessionStore(temp)
        store.rename("s1", "Nurburgring")
        assertEquals("Nurburgring", store.load("s1")?.trackLabel)
    }

    @Test
    fun `sync offset updates the session and its laps`() {
        writeSession("s1", "2026-11-10T10:00:00Z")
        val store = SessionStore(temp)
        store.setSyncOffset("s1", -120)
        val session = store.load("s1")!!
        assertEquals(-120, session.syncOffsetMs)
        assertEquals(-120, session.laps.first().syncOffsetMs)
        val lap = SessionJsonCodec.decodeLap(
            File(temp, "sessions/s1/laps/lap-001.json").readText(),
        )
        assertEquals(-120, lap.syncOffsetMs)
    }

    @Test
    fun `updateLap rewrites the lap list and lap json`() {
        writeSession("s1", "2026-11-10T10:00:00Z")
        val store = SessionStore(temp)
        val lap = store.load("s1")!!.laps.first().copy(needsCut = true)
        store.updateLap("s1", lap)
        assertEquals(true, store.load("s1")!!.laps.first().needsCut)
        assertEquals(
            true,
            SessionJsonCodec.decodeLap(File(temp, "sessions/s1/laps/lap-001.json").readText()).needsCut,
        )
    }

    @Test
    fun `reads lap samples from the binary file`() {
        val dir = writeSession("s1", "2026-11-10T10:00:00Z")
        val sample = Sample(
            timeNanos = 1_000,
            speedMps = 42f,
            throttle = 1,
            brake = 2,
            gear = 3,
            rpm = 4f,
            positionX = 5f,
            positionY = 6f,
            positionZ = 7f,
            lapCount = 1,
            bestLapTimeMs = 0,
            lastLapTimeMs = 0,
            flags = 1,
            carCode = 3474,
            fuel = 0f,
            tyreTempFL = 0f,
            tyreTempFR = 0f,
            tyreTempRL = 0f,
            tyreTempRR = 0f,
            packetId = 1,
        )
        File(dir, "laps/lap-001.gt7lap").writeBytes(LapTelemetryCodec.encode(listOf(sample)))
        assertEquals(listOf(sample), SessionStore(temp).readSamples("s1", 1))
    }
}
