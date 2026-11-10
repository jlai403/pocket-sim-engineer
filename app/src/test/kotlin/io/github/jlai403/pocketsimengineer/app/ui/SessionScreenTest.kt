package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `lists laps with times and opens the tapped lap`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)),
        )
        var openedLap: Int? = null

        compose.setContent {
            PocketSimEngineerTheme {
                SessionScreen(
                    sessionId = "session-1",
                    onBack = {},
                    onOpenLap = { openedLap = it },
                    onDeleted = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1:30.000").assertExists()
        compose.onNodeWithText("1:31.500").assertExists()
        compose.onNodeWithText("2 laps · best 1:30.000").assertExists()

        compose.onAllNodesWithText("Open")[0].performClick()
        assertEquals(1, openedLap)
    }

    @Test
    fun `marks a lap whose video has not been cut`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), needsCut = true),
        )

        compose.setContent {
            PocketSimEngineerTheme {
                SessionScreen(
                    sessionId = "session-1",
                    onBack = {},
                    onOpenLap = {},
                    onDeleted = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Video not cut yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Video not cut yet").assertExists()
        assertTrue(true)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
