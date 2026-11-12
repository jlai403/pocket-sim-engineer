package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
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
class DriverSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearProfiles() {
        DriverProfileStore(context).clear()
    }

    @Test
    fun `use my profile assigns the owner`() {
        DriverProfileStore(context).setOwner(DriverProfile("skino2024", "Skino", "GB"))
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
        var changes = 0

        compose.setContent {
            PocketSimEngineerTheme {
                DriverSection(
                    sessionId = "session-1",
                    lapIndex = 1,
                    driverPsnId = null,
                    onChanged = { changes++ },
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Use my profile").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("driver-use-owner").performClick()
        compose.waitUntil(TIMEOUT) { changes == 1 }
        assertEquals("skino2024", lapDriver())
    }

    @Test
    fun `choosing a cached driver assigns it`() {
        val profiles = DriverProfileStore(context)
        profiles.setOwner(DriverProfile("skino2024", "Skino", "GB"))
        profiles.save(DriverProfile("friend", "Friend", "US"))
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                DriverSection(
                    sessionId = "session-1",
                    lapIndex = 1,
                    driverPsnId = null,
                    onChanged = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Set driver").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("driver-menu").performClick()
        compose.onNodeWithText("Friend").performClick()
        compose.waitUntil(TIMEOUT) { lapDriver() == "friend" }
    }

    @Test
    fun `manual driver entry saves the profile and assigns it`() {
        SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))

        compose.setContent {
            PocketSimEngineerTheme {
                DriverSection(
                    sessionId = "session-1",
                    lapIndex = 1,
                    driverPsnId = null,
                    onChanged = {},
                )
            }
        }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Set driver").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("driver-menu").performClick()
        compose.onNodeWithText("Add driver…").performClick()
        compose.onNodeWithTag("driver-add-psn").performTextInput("friend")
        compose.onNodeWithTag("driver-add-manual").performClick()
        compose.onNodeWithTag("driver-add-name").performTextInput("Friend")
        compose.onNodeWithTag("driver-add-country").performTextInput("US")
        compose.onNodeWithTag("driver-add-save").performClick()

        compose.waitUntil(TIMEOUT) { lapDriver() == "friend" }
        assertEquals("Friend", DriverProfileStore(context).profile("friend")?.nickname)
    }

    @Test
    fun `driver row opens the public profile for the assigned driver`() {
        var opened: String? = null

        compose.setContent {
            PocketSimEngineerTheme {
                DriverPickerRow(
                    driverPsnId = "friend",
                    profile = DriverProfile("friend", "Friend", "US"),
                    owner = null,
                    profiles = emptyList(),
                    onChange = {},
                    onAddDriver = {},
                    onOpenLink = { opened = it },
                )
            }
        }

        compose.onNodeWithTag("driver-profile").performClick()
        compose.onNodeWithText("GT GridStats").performClick()

        assertEquals("https://gt-gridstats.com/player/friend", opened)
    }

    @Test
    fun `driver row hides profile links when the caller does not want them`() {
        compose.setContent {
            PocketSimEngineerTheme {
                DriverPickerRow(
                    driverPsnId = "friend",
                    profile = DriverProfile("friend", "Friend", "US"),
                    owner = null,
                    profiles = emptyList(),
                    onChange = {},
                    onAddDriver = {},
                    onOpenLink = null,
                )
            }
        }

        compose.onNodeWithTag("driver-menu").performClick()
        assertEquals(0, compose.onAllNodesWithText("GT GridStats").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("GTSH-Rank").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("driver-profile").fetchSemanticsNodes().size)
    }

    private fun lapDriver(): String? =
        SessionStore(context.filesDir).lap("session-1", 1)?.driverPsnId

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
