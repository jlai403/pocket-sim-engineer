package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.jlai403.pocketsimengineer.app.service.TelemetryEvent
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
class CaptureScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `recording instructions start collapsed and toggle`() {
        compose.setContent {
            PocketSimEngineerTheme {
                CaptureScreen(
                    state = CaptureUiState(ps5Ip = "192.168.1.50"),
                    onSaveIp = {},
                    onArm = {},
                    onTestRecord = {},
                    onStop = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("HOW TO RECORD").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("7 steps · about a minute before your first lap").assertExists()
        compose.onNodeWithText("Switch to Chiaki-Up and start driving", substring = true)
            .assertDoesNotExist()

        compose.onNodeWithText("Show steps").performScrollTo().performClick()
        compose.onNodeWithText("Switch to Chiaki-Up and start driving", substring = true)
            .assertExists()
        compose.onNodeWithText("Keep in mind").assertExists()

        compose.onNodeWithText("Hide steps").performScrollTo().performClick()
        compose.onNodeWithText("Switch to Chiaki-Up and start driving", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `saving the ip normalizes it and reports invalid input`() {
        var saved: String? = null
        compose.setContent {
            PocketSimEngineerTheme {
                CaptureScreen(
                    state = CaptureUiState(),
                    onSaveIp = { saved = it },
                    onArm = {},
                    onTestRecord = {},
                    onStop = {},
                )
            }
        }

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
        compose.setContent {
            PocketSimEngineerTheme {
                CaptureScreen(
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
                    onSaveIp = {},
                    onArm = {},
                    onTestRecord = {},
                    onStop = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("GT7 STREAM").fetchSemanticsNodes().isNotEmpty()
        }
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
