package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
class FrameRulerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `right drag rewinds by the whole gesture`() {
        val totals = mutableListOf<Int>()
        var pxPerFrame = 0f

        compose.setContent {
            pxPerFrame = with(LocalDensity.current) { 6.dp.toPx() }
            PocketSimEngineerTheme {
                FrameRuler(
                    valueMs = 5_000,
                    startMs = 0,
                    endMs = 10_000,
                    frameStepMs = 33,
                    enabled = true,
                    onScrubStart = {},
                    onScrub = { totals += it },
                    hapticsEnabled = false,
                    modifier = Modifier.testTag("ruler"),
                )
            }
        }

        compose.onNodeWithTag("ruler").performTouchInput {
            down(center)
            moveBy(Offset(pxPerFrame * 5f, 0f))
            moveBy(Offset(pxPerFrame * 15f, 0f))
            up()
        }

        assertTrue("expected two scrub events, got $totals", totals.size >= 2)
        assertTrue("totals must accumulate backward, got $totals", totals.last() <= -16)
    }

    @Test
    fun `scrub start fires once per drag`() {
        var starts = 0
        var pxPerFrame = 0f

        compose.setContent {
            pxPerFrame = with(LocalDensity.current) { 6.dp.toPx() }
            PocketSimEngineerTheme {
                FrameRuler(
                    valueMs = 0,
                    startMs = 0,
                    endMs = 10_000,
                    frameStepMs = 33,
                    enabled = true,
                    onScrubStart = { starts++ },
                    onScrub = {},
                    hapticsEnabled = false,
                    modifier = Modifier.testTag("ruler"),
                )
            }
        }

        compose.onNodeWithTag("ruler").performTouchInput {
            down(center)
            moveBy(Offset(pxPerFrame * 10f, 0f))
            up()
        }

        assertEquals(1, starts)
    }

    @Test
    fun `left drag forwards`() {
        val totals = mutableListOf<Int>()
        var pxPerFrame = 0f

        compose.setContent {
            pxPerFrame = with(LocalDensity.current) { 6.dp.toPx() }
            PocketSimEngineerTheme {
                FrameRuler(
                    valueMs = 5_000,
                    startMs = 0,
                    endMs = 10_000,
                    frameStepMs = 33,
                    enabled = true,
                    onScrubStart = {},
                    onScrub = { totals += it },
                    hapticsEnabled = false,
                    modifier = Modifier.testTag("ruler"),
                )
            }
        }

        compose.onNodeWithTag("ruler").performTouchInput {
            down(center)
            moveBy(Offset(pxPerFrame * -10f, 0f))
            up()
        }

        assertTrue("expected a forward scrub, got $totals", totals.isNotEmpty())
        assertTrue("leftward drag must go forward, got $totals", totals.last() > 0)
    }

    @Test
    fun `disabled ruler ignores drags`() {
        var events = 0
        var pxPerFrame = 0f

        compose.setContent {
            pxPerFrame = with(LocalDensity.current) { 6.dp.toPx() }
            PocketSimEngineerTheme {
                FrameRuler(
                    valueMs = 0,
                    startMs = 0,
                    endMs = 10_000,
                    frameStepMs = 33,
                    enabled = false,
                    onScrubStart = {},
                    onScrub = { events++ },
                    hapticsEnabled = false,
                    modifier = Modifier.testTag("ruler"),
                )
            }
        }

        compose.onNodeWithTag("ruler").performTouchInput {
            down(center)
            moveBy(Offset(pxPerFrame * 10f, 0f))
            up()
        }

        assertEquals(0, events)
    }

    @Test
    fun `scrub end fires once per drag`() {
        var ends = 0
        var pxPerFrame = 0f

        compose.setContent {
            pxPerFrame = with(LocalDensity.current) { 6.dp.toPx() }
            PocketSimEngineerTheme {
                FrameRuler(
                    valueMs = 0,
                    startMs = 0,
                    endMs = 10_000,
                    frameStepMs = 33,
                    enabled = true,
                    onScrubStart = {},
                    onScrub = {},
                    onScrubEnd = { ends++ },
                    hapticsEnabled = false,
                    modifier = Modifier.testTag("ruler"),
                )
            }
        }

        compose.onNodeWithTag("ruler").performTouchInput {
            down(center)
            moveBy(Offset(pxPerFrame * 10f, 0f))
            up()
        }

        assertEquals(1, ends)
    }
}
