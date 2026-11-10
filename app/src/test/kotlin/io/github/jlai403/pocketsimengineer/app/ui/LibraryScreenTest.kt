package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `shows a session with its lap count and best lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                LibraryScreen(onOpenSession = {}, onCapture = {}, onSettings = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("BRZ GT300 '21").assertExists()
        compose.onNodeWithText("1 laps · best 1:30.000").assertExists()
    }

    @Test
    fun `tapping a session reports its id`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened: String? = null

        compose.setContent {
            PocketSimEngineerTheme {
                LibraryScreen(
                    onOpenSession = { opened = it },
                    onCapture = {},
                    onSettings = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1 laps · best 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1 laps · best 1:30.000").performClick()
        assertEquals("session-1", opened)
    }

    @Test
    fun `groups sessions with the same car and track`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "session-2",
                createdAt = "2026-11-10T10:05:00Z",
                lapTimesMs = listOf(91_000),
            ),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "session-3",
                trackLabel = "Nurburgring",
                createdAt = "2026-11-10T11:00:00Z",
                lapTimesMs = listOf(120_000),
            ),
        )

        compose.setContent {
            PocketSimEngineerTheme {
                LibraryScreen(onOpenSession = {}, onCapture = {}, onSettings = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Suzuka").assertCountEquals(1)
        compose.onAllNodesWithText("Nurburgring").assertCountEquals(1)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
