package io.github.jlai403.lapcraft.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.core.session.ExportReport
import io.github.jlai403.lapcraft.core.session.ImportReport
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
class LibrarySectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `library card shows both actions and the lap count`() {
        compose.setContent {
            LapCraftTheme { LibrarySection() }
        }

        compose.onNodeWithTag("settings-library-summary").assertExists()
        compose.onNodeWithTag("settings-library-export").assertExists()
        compose.onNodeWithTag("settings-library-import").assertExists()
    }

    @Test
    fun `result summaries read naturally`() {
        assertEquals("Imported 3 laps, skipped 2 already here.", importSummary(ImportReport(3, 2, 0)))
        assertEquals("Imported 1 lap.", importSummary(ImportReport(1, 0, 0)))
        assertEquals("Imported 3 laps, 1 could not be read.", importSummary(ImportReport(3, 0, 1)))
        assertEquals("Imported 1 lap, fetched 1 profile.", importSummary(ImportReport(1, 0, 0, listOf(DriverProfile("x", "X", "GB")))))
        assertEquals("Exported 3 laps.", exportSummary(ExportReport(3, 1_048_576)))
        assertEquals("Nothing to export.", exportSummary(ExportReport(0, 0)))
    }
}
