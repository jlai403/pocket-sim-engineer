package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class PlaybackFooterTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an enabled footer drives the transport`() {
        var plays = 0
        val steps = mutableListOf<Int>()

        compose.setContent {
            LapCraftTheme {
                PlaybackFooter(
                    cursorMs = 1_000,
                    lapTimeMs = 90_000,
                    frameStepMs = 33,
                    enabled = true,
                    playing = false,
                    looping = false,
                    speed = 1f,
                    events = emptyList(),
                    hapticsEnabled = false,
                    onScrubStart = {},
                    onScrub = {},
                    onScrubEnd = {},
                    onSeekFraction = {},
                    onRewind = {},
                    onPlayPause = { plays++ },
                    onForward = {},
                    onToggleLoop = {},
                    onSpeedChange = {},
                    onStep = { steps += it },
                )
            }
        }

        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Step back one frame").performClick()
        compose.onNodeWithContentDescription("Step forward one frame").performClick()

        assertEquals(1, plays)
        assertEquals(listOf(-1, 1), steps)
        compose.onNodeWithContentDescription("Previous brake point").assertExists()
        compose.onNodeWithContentDescription("Next brake point").assertExists()
        compose.onNodeWithText("Loop").assertExists()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `the compact footer is one row with a ruler`() {
        var plays = 0
        var step = 0
        var loops = 0
        var speed = 1f

        compose.setContent {
            LapCraftTheme {
                PlaybackFooter(
                    cursorMs = 1_000,
                    lapTimeMs = 90_000,
                    frameStepMs = 33,
                    enabled = true,
                    playing = false,
                    looping = false,
                    speed = 1f,
                    events = emptyList(),
                    hapticsEnabled = false,
                    onScrubStart = {},
                    onScrub = {},
                    onScrubEnd = {},
                    onSeekFraction = {},
                    onRewind = {},
                    onPlayPause = { plays++ },
                    onForward = {},
                    onToggleLoop = { loops++ },
                    onSpeedChange = { speed = it },
                    onStep = { step += it },
                    compact = true,
                )
            }
        }

        compose.onNodeWithText("0:00.001 / 1:30.000").assertDoesNotExist()
        compose.onNodeWithContentDescription("Frame ruler").assertExists()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Step forward one frame").performClick()
        compose.onNodeWithContentDescription("Loop lap").performClick()
        compose.onNodeWithTag("speed-cycle").performClick()

        assertEquals(1, plays)
        assertEquals(1, step)
        assertEquals(1, loops)
        assertEquals(1.5f, speed)
    }
}
