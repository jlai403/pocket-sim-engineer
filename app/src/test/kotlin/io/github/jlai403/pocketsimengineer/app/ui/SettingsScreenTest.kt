package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.updater.UpdateManager
import io.github.jlai403.pocketsimengineer.core.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

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
}
