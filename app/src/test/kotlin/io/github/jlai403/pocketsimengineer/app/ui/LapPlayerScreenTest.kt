package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
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
            PocketSimEngineerTheme {
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
        compose.onNodeWithText("Speed").assertExists()
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
            PocketSimEngineerTheme {
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
            PocketSimEngineerTheme {
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
        compose.onNodeWithText("−1 frame").assertDoesNotExist()
        compose.onNodeWithText("+1 frame").assertDoesNotExist()
        compose.onNodeWithText("1×").assertExists()
    }

    @Test
    fun `the speed menu offers playback rates`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
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
        compose.onNodeWithText("1×").performClick()
        compose.onNodeWithText("2×").assertExists()
    }

    @Test
    fun `edit lap opens from the header and offers replay replacement`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
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
            PocketSimEngineerTheme {
                LapPlayerScreen(
                    sessionId = "session-1",
                    lapIndex = 1,
                    onBack = {},
                    onImportSync = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Suzuka · 🇬🇧 Skino").fetchSemanticsNodes().isNotEmpty()
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
            PocketSimEngineerTheme {
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
    }

    @Test
    fun `an existing clip survives a pending replacement import`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(lapTimesMs = listOf(90_000), pendingSource = true),
        )

        compose.setContent {
            PocketSimEngineerTheme {
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
            compose.onAllNodesWithText("Sync pending").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Resume sync").assertExists()
    }

    @Test
    fun `the telemetry hud shows over the replay by default`() {
        SettingsStore(context).telemetryHud = true
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
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
            PocketSimEngineerTheme {
                LapPlayerScreen("session-1", 1, onBack = {}, onImportSync = {})
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().size)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
