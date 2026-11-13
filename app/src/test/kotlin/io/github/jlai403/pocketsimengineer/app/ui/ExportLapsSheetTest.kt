package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.jlai403.pocketsimengineer.core.session.LapRef
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportLapsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val laps = listOf(
        LapSummary(
            sessionId = "session-1",
            lapIndex = 1,
            lapTimeMs = 92_417,
            createdAt = "2026-11-13T10:00:00Z",
            trackLabel = "Suzuka",
            carName = "BRZ GT300 '21",
            hasReplay = true,
            replayPending = false,
            videoBytes = 100,
            telemetryBytes = 20,
        ),
        LapSummary(
            sessionId = "session-2",
            lapIndex = 1,
            lapTimeMs = 90_002,
            createdAt = "2026-11-12T10:00:00Z",
            trackLabel = "Monza",
            carName = "BRZ GT300 '21",
            hasReplay = true,
            replayPending = false,
            videoBytes = 200,
            telemetryBytes = 30,
        ),
    )

    @Test
    fun `all laps are selected by default and totals are summed`() {
        compose.setContent {
            PocketSimEngineerTheme {
                ExportLapsSheet(laps = laps, onDismiss = {}, onExport = {})
            }
        }

        compose.onNodeWithTag("export-laps-summary")
            .assertTextContains("2 of 2 selected · ≈ 350 B", substring = true)
        compose.onNodeWithTag("export-laps-button").assertIsEnabled()
    }

    @Test
    fun `deselecting every lap disables export`() {
        compose.setContent {
            PocketSimEngineerTheme {
                ExportLapsSheet(laps = laps, onDismiss = {}, onExport = {})
            }
        }

        compose.onNodeWithTag("export-lap-session-1-1").performClick()
        compose.onNodeWithTag("export-lap-session-2-1").performClick()

        compose.onNodeWithTag("export-laps-summary").assertTextContains("0 of 2 selected", substring = true)
        compose.onNodeWithTag("export-laps-button").assertIsNotEnabled()
    }

    @Test
    fun `export passes the selected refs`() {
        var exported: List<LapRef>? = null
        compose.setContent {
            PocketSimEngineerTheme {
                ExportLapsSheet(laps = laps, onDismiss = {}, onExport = { exported = it })
            }
        }

        compose.onNodeWithTag("export-lap-session-1-1").performClick()
        compose.onNodeWithTag("export-laps-button").performClick()

        assertEquals(listOf(LapRef("session-2", 1)), exported)
    }

    @Test
    fun `select all toggles between everything and nothing`() {
        compose.setContent {
            PocketSimEngineerTheme {
                ExportLapsSheet(laps = laps, onDismiss = {}, onExport = {})
            }
        }

        compose.onNodeWithTag("export-laps-select-all").performClick()
        compose.onNodeWithTag("export-laps-summary").assertTextContains("0 of 2 selected", substring = true)

        compose.onNodeWithTag("export-laps-select-all").performClick()
        compose.onNodeWithTag("export-laps-summary").assertTextContains("2 of 2 selected", substring = true)
    }
}
