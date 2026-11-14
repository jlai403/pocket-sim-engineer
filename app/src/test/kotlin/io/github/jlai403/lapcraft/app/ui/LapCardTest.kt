package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.core.session.LapSummary
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
            LapCraftTheme {
                LapCard(lap, null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("1:30.000").assertExists()
        compose.onNodeWithText("Suzuka").assertExists()
        compose.onNodeWithText("BRZ GT300 '21").assertExists()
        compose.onNodeWithText("Nov 11").assertExists()
    }

    @Test
    fun `shows the fastest badge`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, true, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Fastest lap").assertExists()
    }

    @Test
    fun `shows the delta when the lap is not the fastest`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, 412, {}, {}, {})
            }
        }

        compose.onNodeWithText("+0.412").assertExists()
    }

    @Test
    fun `shows the driver name and flag`() {
        val friend = DriverProfile("friend", "Friend", "US")

        compose.setContent {
            LapCraftTheme {
                LapCard(lap.copy(driverPsnId = "friend"), friend, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Friend").assertExists()
        compose.onNodeWithText("🇺🇸").assertExists()
    }

    @Test
    fun `shows the psn when the profile is unknown`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap.copy(driverPsnId = "friend"), null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("friend").assertExists()
    }

    @Test
    fun `says driver not set when the lap has none`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Driver not set").assertExists()
    }

    @Test
    fun `shows a saved replay`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap.copy(hasReplay = true), null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Replay").assertExists()
    }

    @Test
    fun `shows a pending sync`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap.copy(replayPending = true), null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithText("Sync pending").assertExists()
    }

    @Test
    fun `tap opens the lap`() {
        var opened = 0

        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, { opened++ }, {}, {})
            }
        }

        compose.onNodeWithTag("lap-card").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `right swipe reveals edit on the left`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithTag("lap-card").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.2f, 0f))
        }
        compose.waitForIdle()

        compose.onNodeWithText("Edit").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
        val left = compose.onNodeWithText("Edit").fetchSemanticsNode().boundsInRoot.left
        assertTrue("edit should sit on the left, was $left", left < 120f)

        compose.onNodeWithTag("lap-card").performTouchInput { up() }
    }

    @Test
    fun `left swipe reveals delete on the right`() {
        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, {}, {}, {})
            }
        }

        compose.onNodeWithTag("lap-card").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.2f, 0f))
        }
        compose.waitForIdle()

        compose.onNodeWithText("Delete").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Edit").fetchSemanticsNodes().size)
        val right = compose.onNodeWithText("Delete").fetchSemanticsNode().boundsInRoot.right
        assertTrue("delete should sit on the right, was $right", right > 200f)

        compose.onNodeWithTag("lap-card").performTouchInput { up() }
    }

    @Test
    fun `swipe back springs to the default state`() {
        var edited = 0
        var deleted = 0

        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, {}, { edited++ }, { deleted++ })
            }
        }

        compose.onNodeWithTag("lap-card").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.2f, 0f))
            moveBy(Offset(-width * 0.2f, 0f))
            up()
        }
        compose.mainClock.advanceTimeBy(2_000)

        assertEquals(0, compose.onAllNodesWithText("Edit").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
        assertEquals(0, edited)
        assertEquals(0, deleted)
    }

    @Test
    fun `swipe actions fill the card height`() {
        compose.setContent {
            LapCraftTheme {
                LazyColumn {
                    item {
                        LapCard(lap, null, false, null, {}, {}, {})
                    }
                }
            }
        }

        compose.onNodeWithTag("lap-card").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.2f, 0f))
        }
        compose.waitForIdle()

        val card = compose.onNodeWithTag("lap-card").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("lap-card-action").fetchSemanticsNode().boundsInRoot

        assertEquals(card.top, action.top, 1f)
        assertEquals(card.height, action.height, 1f)

        compose.onNodeWithTag("lap-card").performTouchInput { up() }
    }

    @Test
    fun `swipe right edits once without opening the lap`() {
        var opened = 0
        var edited = 0

        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, { opened++ }, { edited++ }, {})
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
    fun `swipe left deletes once without opening the lap`() {
        var opened = 0
        var deleted = 0

        compose.setContent {
            LapCraftTheme {
                LapCard(lap, null, false, null, { opened++ }, {}, { deleted++ })
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
