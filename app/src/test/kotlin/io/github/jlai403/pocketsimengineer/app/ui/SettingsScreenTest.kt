package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.settings.VideoQuality
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
    fun `keep raw toggle persists`() {
        compose.setContent {
            PocketSimEngineerTheme { SettingsScreen() }
        }

        compose.onNodeWithTag("keep-raw").performScrollTo().performClick()

        assertTrue(SettingsStore(context).keepRaw)
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
    fun `resolution choice persists`() {
        compose.setContent {
            PocketSimEngineerTheme { SettingsScreen() }
        }

        compose.onNodeWithText("720p60").performClick()

        assertEquals(VideoQuality.P720, SettingsStore(context).quality)
    }
}
