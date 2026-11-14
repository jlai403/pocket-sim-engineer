package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.core.clip.TrackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LapCompareScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun installLaps(referenceClip: Boolean = true) {
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "primary", lapTimesMs = listOf(90_000)),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "reference",
                lapTimesMs = listOf(92_000),
                withReplay = referenceClip,
            ),
        )
    }

    private fun setCompareScreen(onAddClip: (String, Int) -> Unit = { _, _ -> }) {
        compose.setContent {
            LapCraftTheme {
                LapCompareScreen(
                    primarySessionId = "primary",
                    primaryLapIndex = 1,
                    referenceSessionId = "reference",
                    referenceLapIndex = 1,
                    onBack = {},
                    onAddClip = onAddClip,
                )
            }
        }
    }

    @Test
    fun `both laps get a pane, charts, and a shared track map`() {
        installLaps()
        setCompareScreen()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Primary · Lap 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reference · Lap 1").assertExists()
        compose.onNodeWithText("Track map").assertExists()
        compose.onNodeWithText("Track %").assertExists()
        compose.onNodeWithText("Reference").assertExists()
        compose.onNodeWithTag("compare-reference-time").assertExists()
    }

    @Test
    fun `a lap without a clip offers to add one`() {
        installLaps(referenceClip = false)
        var added: Pair<String, Int>? = null
        setCompareScreen { id, index -> added = id to index }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No clip yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("compare-add-clip-Reference").performClick()

        assertEquals("reference" to 1, added)
    }

    @Test
    fun `portrait shows the rotate prompt`() {
        installLaps()
        setCompareScreen()

        compose.onNodeWithTag("compare-rotate").assertExists()
        compose.onNodeWithText("Rotate to landscape").assertExists()
    }

    @Test
    fun `stepping the primary moves the reference cursor`() {
        installLaps()
        setCompareScreen()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Primary · Lap 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("+1 frame").fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        val before = referenceCursorText()
        compose.onNodeWithText("+1 frame").performClick()

        compose.waitUntil(TIMEOUT) { referenceCursorText() != before }
        assertNotEquals(before, referenceCursorText())
    }

    @Test
    fun `the reference cursor is interpolated from track progress`() {
        val primary = TrackProgress.table(lineSamples(lapTimeMs = 1_000), 0)
        val reference = TrackProgress.table(lineSamples(lapTimeMs = 2_000), 0)
        assertNotNull(primary)
        assertNotNull(reference)

        assertEquals(0, referenceCursor(primary, 0, reference, 2_000))
        assertEquals(1_000, referenceCursor(primary, 500, reference, 2_000))
        assertEquals(2_000, referenceCursor(primary, 1_000, reference, 2_000))
    }

    private fun referenceCursorText(): String =
        compose.onNodeWithTag("compare-reference-time")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(" ") { it.text }

    private fun lineSamples(lapTimeMs: Int): List<io.github.jlai403.lapcraft.core.model.Sample> =
        (0..10).map { step ->
            SessionFixtures.sample(
                timeNanos = lapTimeMs.toLong() * step / 10 * 1_000_000L,
                x = step * 10f,
                z = 0f,
            )
        }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
