package io.github.jlai403.pocketsimengineer.app.track

import android.app.Application
import io.github.jlai403.pocketsimengineer.core.model.Sample
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrackCatalogTest {
    private val catalog get() = TrackCatalog(RuntimeEnvironment.getApplication())

    @Test
    fun `names Road Atlanta instead of declining on the Watkins Glen collision`() {
        val lap = lapFrom("/road-atlanta-line.json")

        assertEquals("Michelin Raceway Road Atlanta", catalog.identify(lap))

        val candidates = catalog.candidates(lap)
        assertEquals(listOf("Michelin Raceway Road Atlanta"), candidates.map { it.name })
        assertTrue(candidates.single().coverage > 0.9f)
    }

    @Test
    fun `separates Monza from its no-chicane layout`() {
        assertEquals(
            "Autodromo Nazionale Monza",
            catalog.identify(lapFrom("/monza-line.json")),
        )
        assertEquals(
            "Autodromo Nazionale Monza No Chicane",
            catalog.identify(lapFrom("/monza-no-chicane-line.json")),
        )
    }

    private fun lapFrom(resource: String): List<Sample> {
        val points = Json.decodeFromString<List<List<Double>>>(
            checkNotNull(javaClass.getResourceAsStream(resource)) { "missing $resource" }
                .bufferedReader().use { it.readText() },
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
