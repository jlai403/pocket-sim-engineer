package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
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
class ImportPickScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `lists laps with and without replays`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "with-replay", lapTimesMs = listOf(90_000)),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "without-replay",
                trackLabel = "Nurburgring",
                lapTimesMs = listOf(120_000),
                withReplay = false,
            ),
        )

        compose.setContent {
            LapCraftTheme {
                ImportPickScreen(uri = "content://replay", onPick = { _, _ -> }, onClose = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Replaces current replay").assertExists()
        compose.onNodeWithText("No replay").assertExists()
        compose.onNodeWithText("Replace").assertExists()
        compose.onNodeWithText("Attach").assertExists()
    }

    @Test
    fun `a lap with a replay can be replaced`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var picked: Pair<String, Int>? = null

        compose.setContent {
            LapCraftTheme {
                ImportPickScreen(
                    uri = "content://replay",
                    onPick = { id, index -> picked = id to index },
                    onClose = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Replace").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Replace").performClick()

        assertEquals("session-1" to 1, picked)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
