package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.clip.TrackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w800dp-h360dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LapCompareScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetSettings() {
        SettingsStore(context).telemetryHud = true
    }

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
    fun `the video tab shows both panes with a hud and a readout`() {
        installLaps()
        setCompareScreen()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Ref · Lap 1 · 1:32.000").assertExists()
        assertEquals(2, compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithTag("compare-readout").fetchSemanticsNodes().size)
        compose.onNodeWithText("Track %").assertDoesNotExist()
        compose.onNodeWithTag("compare-delta").assertExists()
    }

    @Test
    fun `the HUD switch hides only the hud`() {
        SettingsStore(context).telemetryHud = false
        installLaps()
        setCompareScreen()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithTag("compare-readout").fetchSemanticsNodes().size)
    }

    @Test
    fun `the telemetry tab shows the per-metric charts`() {
        installLaps()
        setCompareScreen()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("compare-tab-telemetry").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Track %").fetchSemanticsNodes().isNotEmpty()
        }
        listOf("BRK", "THR", "SPD", "RPM", "GEAR").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithText("lap 1").assertExists()
        compose.onNodeWithText("lap 2 (reference)").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("compare-readout").fetchSemanticsNodes().size)
    }

    @Test
    fun `a lap without a clip offers to add one`() {
        installLaps(referenceClip = false)
        var added: Pair<String, Int>? = null
        setCompareScreen { id, index -> added = id to index }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No clip yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("compare-add-clip-reference").performClick()

        assertEquals("reference" to 1, added)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port")
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
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("frame-forward").fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        val before = referenceCursorText()
        compose.onNodeWithTag("frame-forward").performClick()

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
        compose.onNodeWithTag("compare-cursor-reference")
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
