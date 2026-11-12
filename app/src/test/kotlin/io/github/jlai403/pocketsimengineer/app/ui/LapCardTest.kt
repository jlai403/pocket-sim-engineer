package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
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
class LapCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val lap = LapSummary(
        sessionId = "session-1",
        lapIndex = 1,
        lapTimeMs = 90_000,
        createdAt = "2026-11-11T10:00:00Z",
        trackLabel = "Suzuka",
        carName = "BRZ GT300 '21",
        hasReplay = false,
        replayPending = false,
    )

    @Test
    fun `shows the time track and car`() {
        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, null, false, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("1:30.000").assertExists()
        compose.onNodeWithText("Suzuka").assertExists()
        compose.onNodeWithText("BRZ GT300 '21 · 2026-11-11").assertExists()
    }

    @Test
    fun `shows the fastest badge`() {
        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, null, false, true, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Fastest lap").assertExists()
    }

    @Test
    fun `shows the delta when the lap is not the fastest`() {
        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, null, false, false, 412, {}, {}, {})
            }
        }

        compose.onNodeWithText("+0.412").assertExists()
    }

    @Test
    fun `hides the driver when asked`() {
        val friend = DriverProfile("friend", "Friend", "US")

        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, friend, false, false, null, {}, {}, {})
            }
        }

        assertEquals(0, compose.onAllNodesWithText("Friend").fetchSemanticsNodes().size)
    }

    @Test
    fun `shows the driver when asked`() {
        val friend = DriverProfile("friend", "Friend", "US")

        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, friend, true, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Friend").assertExists()
        compose.onNodeWithText("🇺🇸").assertExists()
    }

    @Test
    fun `shows a saved replay`() {
        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap.copy(hasReplay = true), null, false, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Replay").assertExists()
    }

    @Test
    fun `shows a pending sync`() {
        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap.copy(replayPending = true), null, false, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Sync pending").assertExists()
    }

    @Test
    fun `tap opens the lap`() {
        var opened = 0

        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, null, false, false, null, { opened++ }, {}, {})
            }
        }

        compose.onNodeWithTag("lap-card").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `swipe right edits without opening the lap`() {
        var opened = 0
        var edited = 0

        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, null, false, false, null, { opened++ }, { edited++ }, {})
            }
        }

        compose.onNodeWithTag("lap-card").performTouchInput {
            swipeRight(startX = 5f, endX = width - 5f, durationMillis = 300)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, edited)
        assertEquals(0, opened)
    }

    @Test
    fun `swipe left deletes without opening the lap`() {
        var opened = 0
        var deleted = 0

        compose.setContent {
            PocketSimEngineerTheme {
                LapCard(lap, null, false, false, null, { opened++ }, {}, { deleted++ })
            }
        }

        compose.onNodeWithTag("lap-card").performTouchInput {
            swipeLeft(startX = width - 5f, endX = 5f, durationMillis = 300)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, deleted)
        assertEquals(0, opened)
    }
}
