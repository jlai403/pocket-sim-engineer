package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.app.profile.DriverProfileStore
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LapPlayerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPlaybackPreferences() {
        SettingsStore(context).defaultPlaybackSpeed = 1f
        SettingsStore(context).telemetryHud = true
    }

    @Test
    fun `lap without a clip still shows charts and add replay`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), needsCut = true),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No replay attached").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").assertExists()
        compose.onNodeWithText("Speed / Gear").assertExists()
        compose.onNodeWithText("Track map").assertExists()
        compose.onNodeWithText("Lap 1 · 1:30.000").assertExists()
    }

    @Test
    fun `the replay source button offers files and the PlayStation app`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), needsCut = true),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Add replay").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").performClick()
        compose.onNodeWithText("Files").assertExists()
        compose.onNodeWithText("PlayStation App").assertExists()
        compose.onNodeWithText("Pick a video from this phone").assertExists()
    }

    @Test
    fun `lap with a clip does not ask for a replay`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add replay").assertDoesNotExist()
        compose.onNodeWithContentDescription("Frame ruler").assertExists()
        compose.onNodeWithText("1×").assertExists()
    }

    @Test
    fun `the speed stepper moves through the playback rates`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1×").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("speed-up").performClick()
        compose.onNodeWithText("1.5×").assertExists()
        compose.onNodeWithTag("speed-down").performClick()
        compose.onNodeWithText("1×").assertExists()
    }

    @Test
    fun `the track map follows by default`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Follow").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Follow").assertIsSelected()
    }

    @Test
    fun `edit lap opens from the header and offers replay replacement`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit lap").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Replace replay").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the lap header shows the assigned driver`() {
        DriverProfileStore(context).setOwner(DriverProfile("skino2024", "Skino", "GB"))
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), driverPsnId = "skino2024"),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Expand details").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka · 🇬🇧 Skino", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a pending import can be resumed or replaced`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                lapTimesMs = listOf(90_000),
                withReplay = false,
                pendingSource = true,
            ),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit lap").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Resume sync").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Replace replay").assertExists()
    }

    @Test
    fun `an existing clip survives a pending replacement import`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Newer clip waiting to be synced.").assertExists()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Edit lap").performClick()

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Sync pending").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Resume sync").assertExists()
    }

    @Test
    fun `the telemetry hud shows over the replay by default`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen("session-1", 1, onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the hud stays hidden when disabled in settings`() {
        SettingsStore(context).telemetryHud = false
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen("session-1", 1, onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().size)
    }

    @Test
    fun `an unknown driver shows the psn instead of the owner`() {
        DriverProfileStore(context).setOwner(DriverProfile("owner", "Owner", "GB"))
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), driverPsnId = "other"),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen("session-1", 1, onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Expand details").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka · other", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `share video is disabled without a clip`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), needsCut = true),
        )

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen("session-1", 1, onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Share video").assertIsNotEnabled()
    }

    @Test
    fun `compare offers other laps on the same track`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "session-2", lapTimesMs = listOf(91_000)),
        )
        var compared: List<Any>? = null

        compose.setContent {
            LapCraftTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                    onCompare = { a, b, c, d -> compared = listOf(a, b, c, d) },
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("lap-compare").fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        compose.onNodeWithTag("lap-compare").performClick()
        compose.onNodeWithText("Compare with").assertExists()
        compose.onNodeWithText("1:31.000").performClick()

        assertEquals(listOf<Any>("session-1", 1, "session-2", 1), compared)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
