package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import org.junit.Rule
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
            PocketSimEngineerTheme {
                ChartColumn(samples = samples, lap = lap, cursorMs = 1_000)
            }
        }

        compose.onNodeWithText("Throttle / Brake").assertExists()
        compose.onNodeWithText("Speed / Gear").assertExists()
        compose.onNodeWithText("FL").assertExists()
        compose.onNodeWithText("RR").assertExists()
    }

    @Test
    fun `telemetry with no samples says so`() {
        compose.setContent {
            PocketSimEngineerTheme {
                ChartColumn(samples = emptyList(), lap = lap, cursorMs = 0)
            }
        }

        compose.onNodeWithText("No telemetry for this lap").assertExists()
    }

    @Test
    fun `a single sample has no track map path`() {
        compose.setContent {
            PocketSimEngineerTheme {
                TrackMap(samples = listOf(samples.first()), lap = lap, cursorMs = 0)
            }
        }

        compose.onNodeWithText("No track positions for this lap").assertExists()
    }

    @Test
    fun `track map shows ghost, follow, and turn legend`() {
        compose.setContent {
            PocketSimEngineerTheme {
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

        compose.onNodeWithText("Turn-in").assertExists()
        compose.onNodeWithText("Apex").assertExists()
        compose.onNodeWithText("Exit").assertExists()
        compose.onNodeWithText("Ghost").assertExists()
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
}
