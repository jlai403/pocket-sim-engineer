package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LapPlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `lap without a clip still shows charts and add replay`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), needsCut = true),
        )

        compose.setContent {
            PocketSimEngineerTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No replay attached").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").assertExists()
        compose.onNodeWithText("PS App").assertExists()
        compose.onNodeWithText("Speed").assertExists()
        compose.onNodeWithText("Track map").assertExists()
        compose.onNodeWithText("Lap 1 · 1:30.000").assertExists()
    }

    @Test
    fun `lap with a clip does not ask for a replay`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").assertDoesNotExist()
        compose.onNodeWithText("Replace replay").assertExists()
    }

    @Test
    fun `a pending import can be resumed or replaced`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                lapTimesMs = listOf(90_000),
                withReplay = false,
                pendingSource = true,
            ),
        )

        compose.setContent {
            PocketSimEngineerTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Resume sync").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose another clip").assertExists()
    }

    @Test
    fun `an existing clip survives a pending replacement import`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true),
        )

        compose.setContent {
            PocketSimEngineerTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Replace replay").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Resume import").assertExists()
        compose.onNodeWithText("The current replay still plays.", substring = true).assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
