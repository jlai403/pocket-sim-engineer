package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.jlai403.lapcraft.app.service.TelemetryEvent
import io.github.jlai403.lapcraft.core.capture.CaptureState
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
class RecordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun content(
        state: CaptureUiState,
        onSaveIp: (String) -> Unit = {},
        onConnect: (String) -> Unit = {},
        onCapture: () -> Unit = {},
        onCancelCapture: () -> Unit = {},
        onOpenLap: (String, Int) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            LapCraftTheme {
                RecordScreen(
                    state = state,
                    onSaveIp = onSaveIp,
                    onConnect = onConnect,
                    onDisconnect = {},
                    onCapture = onCapture,
                    onCancelCapture = onCancelCapture,
                    onOpenLap = onOpenLap,
                )
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("GT7 STREAM").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `capture is enabled only after a line has been observed`() {
        var captures = 0
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
            ),
            onCapture = { captures++ },
        )

        compose.onNodeWithText("Capture this lap").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Capture this lap").performClick()
        assertEquals(1, captures)
    }

    @Test
    fun `capture waits for the start line`() {
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = false,
            ),
        )

        compose.onNodeWithText("Capture this lap").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cross the start line to begin a lap.").assertExists()
    }

    @Test
    fun `capturing shows progress and cancel`() {
        var cancelled = 0
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
                capture = CaptureState.Capturing,
            ),
            onCancelCapture = { cancelled++ },
        )

        compose.onNodeWithText("Capturing lap — finishes at the next line").assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `saved lap can be opened`() {
        var opened: Pair<String, Int>? = null
        content(
            state = CaptureUiState(
                ps5Ip = "192.168.1.50",
                telemetryRunning = true,
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapStartObserved = true,
                capture = CaptureState.Saved(sessionId = "lap-7", lapTimeMs = 159_831),
            ),
            onOpenLap = { id, index -> opened = id to index },
        )

        compose.onNodeWithText("Saved 2:39.831").assertExists()
        compose.onNodeWithText("View lap").performScrollTo().performClick()
        assertEquals("lap-7" to 1, opened)
    }

    @Test
    fun `saving the ip normalizes it and reports invalid input`() {
        var saved: String? = null
        content(
            state = CaptureUiState(),
            onSaveIp = { saved = it },
        )

        compose.onNodeWithTag("ps5-ip").performScrollTo().performTextInput("192.168.001.050")
        compose.onNodeWithText("Save IP").performScrollTo().performClick()

        assertEquals("192.168.1.50", saved)
        compose.onNodeWithText("Saved 192.168.1.50").assertExists()

        compose.onNodeWithTag("ps5-ip").performScrollTo().performTextClearance()
        compose.onNodeWithTag("ps5-ip").performTextInput("999.1.1.1")
        compose.onNodeWithText("Save IP").performScrollTo().performClick()

        compose.onNodeWithText("Enter a valid IPv4 address").assertExists()
    }

    @Test
    fun `stream details show decoded fields and lap events`() {
        content(
            state = CaptureUiState(
                connected = true,
                packetsPerSecond = 59.8,
                lastPacketAgeMs = 12,
                lapCount = 3,
                speedMps = 50f,
                gear = 4,
                throttle = 200,
                rpm = 7_000f,
                packetId = 84_213,
                carCode = 3474,
                flags = 1,
                lastLapTimeMs = 90_456,
                acceptedPackets = 12_345,
                completedLaps = 2,
                events = listOf(
                    TelemetryEvent(1_760_000_000_000L, "Lap 2 complete · 1:30.456"),
                ),
            ),
        )

        compose.onNodeWithText("Streaming").assertExists()
        compose.onNodeWithText("Show details").performScrollTo().performClick()

        compose.onNodeWithText("84213").assertExists()
        compose.onNodeWithText("3474").assertExists()
        compose.onNodeWithText("0x0001 · on track").assertExists()
        compose.onNodeWithText("Lap 2 complete · 1:30.456").assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
