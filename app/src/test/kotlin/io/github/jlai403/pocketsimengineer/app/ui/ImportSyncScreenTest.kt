package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
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
        compose.onNodeWithText("Sync replay").assertExists()
    }
}
