package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.core.track.TrackCandidate
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
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearProfile() {
        DriverProfileStore(context).clear()
    }

    @Test
    fun `shows the saved driver name and profile links`() {
        DriverProfileStore(context).save(DriverProfile("skino2024", "Skino", "GB"))
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Skino").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("skino2024").assertExists()
        compose.onNodeWithText("🇬🇧").assertExists()
        compose.onNodeWithTag("library-profile-menu").performClick()
        compose.onNodeWithText("GT GridStats").assertExists()
        compose.onNodeWithText("GTSH-Rank").assertExists()
        compose.onNodeWithText("Edit profile").assertExists()
    }

    @Test
    fun `add profile opens settings`() {
        var edits = 0
        compose.setContent {
            PocketSimEngineerTheme {
                LibraryScreen(onOpenLap = { _, _ -> }, onOpenSettings = { edits++ })
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Add GT profile").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library-add-profile").performClick()

        assertEquals(1, edits)
    }

    @Test
    fun `shows lap time and replay badge`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Suzuka · BRZ GT300 '21").assertExists()
        compose.onNodeWithText("Replay").assertExists()
    }

    @Test
    fun `tapping open reports the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened: Pair<String, Int>? = null

        compose.setContent {
            PocketSimEngineerTheme {
                LibraryScreen(onOpenLap = { id, index -> opened = id to index })
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Open").performClick()
        assertEquals("session-1" to 1, opened)
    }

    @Test
    fun `filters to laps without a replay`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(id = "with-replay", lapTimesMs = listOf(90_000)),
        )
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "without-replay",
                trackLabel = "Nurburgring",
                createdAt = "2026-11-11T10:00:00Z",
                lapTimesMs = listOf(120_000),
                needsCut = true,
            ),
        )

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("2:00.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Without replay").performClick()

        compose.onAllNodesWithText("Nurburgring · BRZ GT300 '21")
            .fetchSemanticsNodes().isNotEmpty()
        assertEquals(
            0,
            compose.onAllNodesWithText("Suzuka · BRZ GT300 '21").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `sort menu switches to fastest`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Newest").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Newest").performClick()
        compose.onNodeWithText("Fastest").performClick()

        compose.onNodeWithText("Fastest").assertExists()
    }

    @Test
    fun `unknown lap offers a track chooser`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(trackLabel = "Unknown", lapTimesMs = listOf(90_000)),
        )

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Choose track").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose track").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Enter a name…").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Enter a name…").assertExists()
    }

    @Test
    fun `track chooser lists candidates and reports the pick`() {
        var picked: String? = null
        var custom = 0
        compose.setContent {
            PocketSimEngineerTheme {
                TrackChoiceButton(
                    unknown = true,
                    expanded = true,
                    candidates = listOf(
                        TrackCandidate("Road Atlanta", "a", 1f),
                        TrackCandidate("Watkins Glen Short", "b", 0.4f),
                    ),
                    onOpen = {},
                    onDismiss = {},
                    onPick = { picked = it },
                    onCustom = { custom++ },
                )
            }
        }

        compose.onNodeWithText("Road Atlanta").assertExists()
        compose.onNodeWithText("Watkins Glen Short").performClick()
        assertEquals("Watkins Glen Short", picked)

        compose.onNodeWithText("Enter a name…").performClick()
        assertEquals(1, custom)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
