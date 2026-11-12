package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun clearState() {
        DriverProfileStore(context).clear()
        SettingsStore(context).previewEmptyLibrary = false
    }

    @Test
    fun `shows the library header with counts and the best lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)))

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Library").assertExists()
        compose.onNodeWithText("2 laps · 1 track · Best 1:30.000").assertExists()
    }

    @Test
    fun `tapping a card opens the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened: Pair<String, Int>? = null

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { id, index -> opened = id to index }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1:30.000").performClick()

        assertEquals("session-1" to 1, opened)
    }

    @Test
    fun `marks the fastest lap per track and shows the delta`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000, 91_500)))

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, compose.onAllNodesWithText("Fastest lap").fetchSemanticsNodes().size)
        compose.onNodeWithText("+1.500").assertExists()
    }

    @Test
    fun `filters to laps without a replay`() {
        SessionFixtures.install(context, SessionFixtures.session(id = "with", lapTimesMs = listOf(90_000)))
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "without",
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
        compose.onNodeWithText("No replay").performClick()

        assertEquals(0, compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().size)
        compose.onNodeWithText("2:00.000").assertExists()
    }

    @Test
    fun `hides the owner on their own lap and shows another driver`() {
        val profiles = DriverProfileStore(context)
        profiles.setOwner(DriverProfile("skino2024", "Skino", "GB"))
        profiles.save(DriverProfile("friend", "Friend", "US"))
        SessionFixtures.install(context, SessionFixtures.session(id = "mine", lapTimesMs = listOf(90_000), driverPsnId = "skino2024"))
        SessionFixtures.install(
            context,
            SessionFixtures.session(
                id = "theirs",
                trackLabel = "Monza",
                createdAt = "2026-11-11T10:00:00Z",
                lapTimesMs = listOf(100_000),
                driverPsnId = "friend",
            ),
        )

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:40.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            1,
            compose.onAllNodesWithTag("lap-driver", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        compose.onNodeWithText("Friend").assertExists()
    }

    @Test
    fun `unknown laps never claim a fastest badge`() {
        SessionFixtures.install(
            context,
            SessionFixtures.session(trackLabel = "Unknown", lapTimesMs = listOf(90_000, 91_000)),
        )

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithText("Fastest lap").fetchSemanticsNodes().size)
    }

    @Test
    fun `empty library explains where to capture`() {
        var record = 0

        compose.setContent {
            PocketSimEngineerTheme {
                LibraryScreen(onOpenLap = { _, _ -> }, onOpenRecord = { record++ })
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No laps yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(
            "Connect to GT7 on the Record tab to start capturing a lap. " +
                "A replay can be added later to the recorded lap.",
        ).assertExists()
        compose.onNodeWithText("Go to Record").performClick()
        assertEquals(1, record)
    }

    @Test
    fun `developer preview shows the empty state and turns off`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        SettingsStore(context).previewEmptyLibrary = true

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Previewing the empty library").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No laps yet").assertExists()
        assertEquals(0, compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().size)

        compose.onNodeWithText("Turn off").performClick()

        assertFalse(SettingsStore(context).previewEmptyLibrary)
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `swipe right opens the edit sheet without opening the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened = 0

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> opened++ }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lap-card").performTouchInput { swipeRight() }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Edit lap").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, opened)
    }

    @Test
    fun `swipe left asks to delete without opening the lap`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var opened = 0

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> opened++ }) }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("1:30.000").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lap-card").performTouchInput { swipeLeft() }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Delete lap?").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, opened)
    }

    @Test
    fun `shows the saved driver chip and profile links`() {
        DriverProfileStore(context).setOwner(DriverProfile("skino2024", "Skino", "GB"))

        compose.setContent {
            PocketSimEngineerTheme { LibraryScreen(onOpenLap = { _, _ -> }) }
        }

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

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
