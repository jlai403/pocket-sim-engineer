package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class ReplaySourceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `source pill reports files and playstation`() {
        var files = 0
        var playStation = 0

        compose.setContent {
            PocketSimEngineerTheme {
                ReplaySourcePill(
                    text = "Add replay",
                    primary = true,
                    onFiles = { files++ },
                    onPlayStation = { playStation++ },
                )
            }
        }

        compose.onNodeWithText("Add replay").performClick()
        compose.onNodeWithText("Files").performClick()
        assertEquals(1, files)

        compose.onNodeWithText("Add replay").performClick()
        compose.onNodeWithText("PlayStation App").performClick()
        assertEquals(1, playStation)
    }
}
