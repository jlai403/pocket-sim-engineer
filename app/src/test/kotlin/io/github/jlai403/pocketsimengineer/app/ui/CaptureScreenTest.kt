package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                    onIpChanged = {},
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

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
