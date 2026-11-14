package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.jlai403.lapcraft.core.clip.TrackProgress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.jlai403.lapcraft.core.session.LapMetadata
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelemetryChartsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `telemetry shows the combined pedal chart and tire labels`() {
        compose.setContent {
            LapCraftTheme {
                ChartColumn(samples = samples, lap = lap, cursorMs = 1_000)
            }
        }

        compose.onNodeWithText("Throttle / Brake").assertExists()
        compose.onNodeWithText("Speed / Gear").assertExists()
        compose.onNodeWithText("FL").assertExists()
        compose.onNodeWithText("RR").assertExists()
    }

    @Test
    fun `steering wheel appears when the lap recorded steering`() {
        compose.setContent {
            LapCraftTheme {
                ChartColumn(
                    samples = samples.map { it.copy(steering = 0.5f) },
                    lap = lap,
                    cursorMs = 1_000,
                )
            }
        }

        compose.onNodeWithTag("steering-wheel").assertExists()
        compose.onNodeWithText("STEER").assertExists()
    }

    @Test
    fun `steering wheel is hidden without steering`() {
        compose.setContent {
            LapCraftTheme {
                ChartColumn(samples = samples, lap = lap, cursorMs = 1_000)
            }
        }

        compose.onNodeWithTag("steering-wheel").assertDoesNotExist()
        compose.onNodeWithText("STEER").assertDoesNotExist()
    }

    @Test
    fun `telemetry with no samples says so`() {
        compose.setContent {
            LapCraftTheme {
                ChartColumn(samples = emptyList(), lap = lap, cursorMs = 0)
            }
        }

        compose.onNodeWithText("No telemetry for this lap").assertExists()
    }

    @Test
    fun `a single sample has no track map path`() {
        compose.setContent {
            LapCraftTheme {
                TrackMap(samples = listOf(samples.first()), lap = lap, cursorMs = 0)
            }
        }

        compose.onNodeWithText("No track positions for this lap").assertExists()
    }

    @Test
    fun `track legend lists brake, throttle, and ghost`() {
        compose.setContent {
            LapCraftTheme { TrackMapLegend() }
        }

        compose.onNodeWithText("Brake").assertExists()
        compose.onNodeWithText("Throttle").assertExists()
        compose.onNodeWithText("Ghost").assertExists()
        compose.onNodeWithText("Turn-in").assertDoesNotExist()
        compose.onNodeWithText("Apex").assertDoesNotExist()
        compose.onNodeWithText("Exit").assertDoesNotExist()
    }

    @Test
    fun `track map renders with a ghost and follow`() {
        compose.setContent {
            LapCraftTheme {
                TrackMap(
                    samples = samples,
                    lap = lap,
                    cursorMs = 1_000,
                    ghost = GhostLap(lap, samples),
                    showGhost = true,
                    follow = true,
                )
            }
        }
    }

    @Test
    fun `charts are labelled with track progress`() {
        compose.setContent {
            LapCraftTheme {
                ChartColumn(samples = samples, lap = lap, cursorMs = 1_000)
            }
        }

        compose.onNodeWithText("\u00b15 s").assertExists()
    }

    @Test
    fun `a reference lap overlays its traces`() {
        compose.setContent {
            LapCraftTheme {
                ChartColumn(
                    samples = samples,
                    lap = lap,
                    cursorMs = 1_000,
                    reference = GhostLap(lap, samples.reversed()),
                )
            }
        }

        compose.onNodeWithText("Throttle / Brake").assertExists()
        compose.onNodeWithText("Speed / Gear").assertExists()
    }

    @Test
    fun `compare charts render every metric and the legend`() {
        compose.setContent {
            LapCraftTheme {
                CompareCharts(
                    samples = samples,
                    lap = lap,
                    reference = GhostLap(lap, samples),
                    cursorMs = 1_000,
                    imperial = false,
                )
            }
        }

        listOf("BRK", "THR", "SPD", "RPM", "GEAR", "\u00b15 s").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithText("lap 1").assertExists()
        compose.onNodeWithText("lap 2 (reference)").assertExists()
    }

    @Test
    fun `compare charts plot the reference on an aligned axis`() {
        val alignment = TrackProgress.align(samples, samples, 0)

        compose.setContent {
            LapCraftTheme {
                CompareCharts(
                    samples = samples,
                    lap = lap,
                    reference = GhostLap(lap, samples),
                    cursorMs = 1_000,
                    imperial = false,
                    alignment = alignment,
                )
            }
        }

        listOf("BRK", "THR", "SPD", "RPM", "GEAR", "\u00b15 s").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun `track legend lists reference in compare mode`() {
        compose.setContent {
            LapCraftTheme { TrackMapLegend(showReference = true) }
        }

        compose.onNodeWithText("Reference").assertExists()
        compose.onNodeWithText("Ghost").assertDoesNotExist()
    }

    @Test
    fun `track map renders a reference line and cursor`() {
        compose.setContent {
            LapCraftTheme {
                TrackMap(
                    samples = samples,
                    lap = lap,
                    cursorMs = 1_000,
                    reference = GhostLap(lap, samples),
                    referenceCursorMs = 2_000,
                    follow = true,
                )
            }
        }
    }

    private val lap = LapMetadata(
        index = 1,
        lapTimeMs = 90_000,
        startNanos = 0,
        endNanos = 90_000_000_000L,
        sampleCount = 2,
        videoFile = "",
        leadInMs = 0,
    )

    private val samples = listOf(
        SessionFixtures.sample(0, brake = 0, throttle = 200, x = 0f, z = 0f),
        SessionFixtures.sample(90_000_000_000L, brake = 200, throttle = 0, x = 100f, z = 100f),
    )

    @Test
    fun `speedText formats in kmh and mph`() {
        assertEquals("45", speedText(12.5f, imperial = false))
        assertEquals("28", speedText(12.5f, imperial = true))
    }

    @Test
    fun `followAnchor centres the driver when requested`() {
        val size = Size(320f, 230f)
        assertEquals(Offset(160f, 115f), followAnchor(size, centred = true))
        assertEquals(Offset(160f, 230f * 0.78f), followAnchor(size, centred = false))
    }

    @Test
    fun `chartWindow centres the cursor in the middle of the lap`() {
        val w = chartWindow(cursorMs = 60_000, lapTimeMs = 90_000)
        assertEquals(55_000, w.startMs)
        assertEquals(CHART_WINDOW_MS, w.windowMs)
    }

    @Test
    fun `chartWindow clamps to the lap start`() {
        assertEquals(0, chartWindow(cursorMs = 1_000, lapTimeMs = 90_000).startMs)
    }

    @Test
    fun `chartWindow clamps to the lap end`() {
        assertEquals(80_000, chartWindow(cursorMs = 89_000, lapTimeMs = 90_000).startMs)
    }

    @Test
    fun `chartWindow shrinks for a short lap`() {
        val w = chartWindow(cursorMs = 2_000, lapTimeMs = 4_000)
        assertEquals(0, w.startMs)
        assertEquals(4_000, w.windowMs)
    }
}
