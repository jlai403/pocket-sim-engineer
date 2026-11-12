package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.updater.UpdateManager
import io.github.jlai403.pocketsimengineer.core.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearProfile() {
        DriverProfileStore(context).clear()
    }

    @Test
    fun `shows the driver profile fields`() {
        compose.setContent {
            PocketSimEngineerTheme { SettingsScreen() }
        }

        compose.onNodeWithText("DRIVER PROFILE").assertExists()
        compose.onNodeWithText("PSN ID").assertExists()
        compose.onNodeWithText("Fetch profile").assertExists()
        compose.onNodeWithText("Fetching sends only your PSN ID to gt-gridstats.com.").assertExists()
    }

    @Test
    fun `fetching a profile saves it`() {
        compose.setContent {
            PocketSimEngineerTheme {
                SettingsScreen(
                    lookupProfile = { psn -> DriverProfile(psn, "Skino", "GB") },
                )
            }
        }

        compose.onNodeWithTag("settings-profile-psn").performTextInput("skino2024")
        compose.onNodeWithTag("settings-profile-fetch").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Skino").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("Skino", DriverProfileStore(context).load()?.nickname)
        compose.onNodeWithText("🇬🇧").assertExists()
    }

    @Test
    fun `a failed lookup keeps the stored profile`() {
        DriverProfileStore(context).save(DriverProfile("skino2024", "Skino", "GB"))
        compose.setContent {
            PocketSimEngineerTheme {
                SettingsScreen(
                    lookupProfile = { throw IOException("No GT GridStats profile found for that PSN ID.") },
                )
            }
        }

        compose.onNodeWithTag("settings-profile-psn").performTextInput("other")
        compose.onNodeWithTag("settings-profile-fetch").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("No GT GridStats profile found for that PSN ID.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("Skino", DriverProfileStore(context).load()?.nickname)
        compose.onNodeWithText("Skino").assertExists()
    }

    @Test
    fun `removing the profile clears it`() {
        DriverProfileStore(context).save(DriverProfile("skino2024", "Skino", "GB"))
        compose.setContent {
            PocketSimEngineerTheme { SettingsScreen() }
        }

        compose.onNodeWithTag("settings-profile-remove").performClick()

        assertNull(DriverProfileStore(context).load())
        compose.onNodeWithText("Fetch profile").assertExists()
    }

    @Test
    fun `shows the version and build identity`() {
        compose.setContent {
            PocketSimEngineerTheme { SettingsScreen() }
        }

        compose.onNodeWithText("Version").assertExists()
        compose.onNodeWithText("Build number").assertExists()
        compose.onNodeWithText("Commit").assertExists()
    }

    @Test
    fun `shows an available update and downloads it`() {
        var downloads = 0
        compose.setContent {
            PocketSimEngineerTheme {
                SettingsScreen(
                    update = UpdateManager.State.Available(
                        UpdateManifest(
                            versionCode = 1035,
                            versionName = "0.1.1",
                            notes = "Build 35",
                        ),
                    ),
                    onDownloadUpdate = { downloads++ },
                )
            }
        }

        compose.onNodeWithText("Version 0.1.1 (build 1035) is available. Build 35").assertExists()
        compose.onNodeWithText("Download 0.1.1").performScrollTo().performClick()
        assertEquals(1, downloads)
    }

    @Test
    fun `up to date says so`() {
        compose.setContent {
            PocketSimEngineerTheme {
                SettingsScreen(update = UpdateManager.State.UpToDate)
            }
        }

        compose.onNodeWithText("You are on the latest build.").assertExists()
        compose.onNodeWithText("Check for updates").assertExists()
    }

    @Test
    fun `imperial units toggle persists`() {
        compose.setContent {
            PocketSimEngineerTheme { SettingsScreen() }
        }

        compose.onNodeWithTag("imperial").performScrollTo().performClick()

        assertTrue(SettingsStore(context).imperialUnits)
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
