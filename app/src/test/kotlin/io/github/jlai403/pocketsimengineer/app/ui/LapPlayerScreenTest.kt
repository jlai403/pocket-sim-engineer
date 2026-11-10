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
    fun `renders charts and refuses an uncut lap video`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), needsCut = true),
        )

        compose.setContent {
            PocketSimEngineerTheme {
                LapPlayerScreen(sessionId = "session-1", lapIndex = 1, onBack = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap video is not available yet", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Lap video is not available yet", substring = true).assertExists()
        compose.onNodeWithText("Speed").assertExists()
        compose.onNodeWithText("Track map").assertExists()
        compose.onNodeWithText("Lap 1 · 1:30.000").assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
