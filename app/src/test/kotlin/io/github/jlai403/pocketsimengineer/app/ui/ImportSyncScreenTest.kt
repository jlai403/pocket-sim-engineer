package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.settings.ReplayPreset
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportSyncScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `missing replay is explained`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                ImportSyncScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(10_000L) {
            compose.onAllNodesWithText("No replay file attached.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sync clip").assertExists()
    }

    @Test
    fun `replacing a clip says the current one is kept on discard`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                ImportSyncScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Saving replaces the current replay. Discard keeps it.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Saving replaces the current replay. Discard keeps it.")
            .assertExists()
    }

    @Test
    fun `sync screen offers transport, fine steps, and anchor guidance`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                ImportSyncScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("How to find the finish frame").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Frame ruler").assertExists()
        compose.onNodeWithContentDescription("Rewind 10 seconds").assertExists()
        compose.onNodeWithContentDescription("Forward 10 seconds").assertExists()
        compose.onNodeWithContentDescription("Play").assertExists()
        compose.onNodeWithText("Anchor: the finish-line frame that ends the lap").assertExists()
        compose.onNodeWithText("−1 frame").assertExists()
        compose.onNodeWithText("+1 frame").assertExists()
    }

    @Test
    fun `anchor tips explain which frame to pick`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                ImportSyncScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("How to find the finish frame").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("How to find the finish frame").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText(
                "No lap timer? Frame-step until the front bumper touches the start/finish line.",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("The playhead starts at the clip's end, so rewind instead of fast-forwarding.")
            .assertExists()
    }

    @Test
    fun `import screen offers per-lap quality controls`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SettingsStore(context).replayPreset = ReplayPreset.Balanced

        compose.setContent {
            PocketSimEngineerTheme {
                ImportSyncScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    sourceUri = null,
                    onDone = {},
                    onCancel = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("REPLAY QUALITY").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("Preset").assertExists()
        compose.onNodeWithText("1080p HEVC ~5 Mbps", substring = true).assertExists()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
