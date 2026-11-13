package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

        compose.setContent {
            PocketSimEngineerTheme {
                PlaybackFooter(
                    cursorMs = 1_000,
                    lapTimeMs = 90_000,
                    frameStepMs = 33,
                    enabled = true,
                    playing = false,
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
                    onSpeedChange = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Play").performClick()

        assertEquals(1, plays)
        compose.onNodeWithContentDescription("Previous brake point").assertExists()
        compose.onNodeWithContentDescription("Next brake point").assertExists()
    }
}
