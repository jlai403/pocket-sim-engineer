package io.github.jlai403.pocketsimengineer.app.track

import android.app.Application
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.LapTelemetryCodec
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrackNamerTest {
    @Test
    fun `backfills an unknown legacy lap`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SessionStore(context.filesDir)
        val samples = roadAtlantaLap()
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = 95_000,
            startNanos = samples.first().timeNanos,
            endNanos = samples.last().timeNanos,
            sampleCount = samples.size,
            videoFile = "",
            videoStartOffsetMs = 0,
        )
        store.save(
            SessionMetadata(
                sessionId = "legacy",
                createdAt = "2026-11-10T10:00:00Z",
                trackLabel = TrackNamer.UNKNOWN,
                carCode = 3474,
                carName = "Car #3474",
                rawFile = "",
                laps = listOf(lap),
            ),
        )
        File(store.sessionDir("legacy"), "laps").mkdirs()
        File(store.sessionDir("legacy"), "laps/lap-001.gt7lap")
            .writeBytes(LapTelemetryCodec.encode(samples))

        val renamed = runBlocking {
            TrackNamer(store, TrackCatalog(context)).identifyUnknownLaps()
        }

        assertEquals(1, renamed)
        assertEquals("Michelin Raceway Road Atlanta", store.load("legacy")?.trackLabel)
    }

    private fun roadAtlantaLap(): List<Sample> {
        val points = Json.decodeFromString<List<List<Double>>>(
            checkNotNull(javaClass.getResourceAsStream("/road-atlanta-line.json")) {
                "missing Road Atlanta fixture"
            }.bufferedReader().use { it.readText() },
        )
        return points.mapIndexed { index, point ->
            Sample(
                timeNanos = index * 16_666_667L,
                speedMps = 0f,
                throttle = 0,
                brake = 0,
                gear = 1,
                rpm = 0f,
                positionX = point[0].toFloat(),
                positionY = 0f,
                positionZ = point[1].toFloat(),
                lapCount = 1,
                bestLapTimeMs = 0,
                lastLapTimeMs = 0,
                flags = 0,
                carCode = 0,
                fuel = 0f,
                tyreTempFL = 0f,
                tyreTempFR = 0f,
                tyreTempRL = 0f,
                tyreTempRR = 0f,
                packetId = 0,
            )
        }
    }
}
