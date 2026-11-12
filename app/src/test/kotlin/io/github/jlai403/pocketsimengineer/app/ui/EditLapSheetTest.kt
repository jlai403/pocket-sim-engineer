package io.github.jlai403.pocketsimengineer.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import io.github.jlai403.pocketsimengineer.core.track.TrackCandidate
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
class EditLapSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val lap = LapSummary(
        sessionId = "session-1",
        lapIndex = 1,
        lapTimeMs = 90_000,
        createdAt = "2026-11-11T10:00:00Z",
        trackLabel = "Suzuka",
        carName = "BRZ GT300 '21",
        hasReplay = false,
        replayPending = false,
    )

    @Test
    fun `saves the trimmed track name`() {
        var saved: String? = null

        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(
                    lap = lap,
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = { saved = it },
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Edit lap").assertExists()
        compose.onNodeWithTag("edit-track").performTextReplacement("  Suzuka East  ")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals("Suzuka East", saved)
    }

    @Test
    fun `offers track candidates for an unknown lap`() {
        var saved: String? = null

        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(
                    lap = lap.copy(trackLabel = "Unknown"),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = listOf(TrackCandidate("Road Atlanta", "a", 1f)),
                    onDismiss = {},
                    onSaveTrack = { saved = it },
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Road Atlanta").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals("Road Atlanta", saved)
    }

    @Test
    fun `shows the assigned driver without profile links`() {
        val friend = DriverProfile("friend", "Friend", "US")

        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(
                    lap = lap.copy(driverPsnId = "friend"),
                    driver = friend,
                    owner = null,
                    profiles = listOf(friend),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = {},
                    onOpenPlayStation = {},
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Friend").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("driver-profile").fetchSemanticsNodes().size)
        compose.onNodeWithTag("driver-menu").performClick()
        assertEquals(0, compose.onAllNodesWithText("GT GridStats").fetchSemanticsNodes().size)
    }

    @Test
    fun `replace replay opens the playstation app from its own row`() {
        var addReplay = 0
        var playStation = 0

        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(
                    lap = lap.copy(hasReplay = true),
                    driver = null,
                    owner = null,
                    profiles = emptyList(),
                    candidates = null,
                    onDismiss = {},
                    onSaveTrack = {},
                    onAssignDriver = {},
                    onAddReplay = { addReplay++ },
                    onOpenPlayStation = { playStation++ },
                    onResumeSync = {},
                )
            }
        }

        compose.onNodeWithText("Replace replay").performClick()
        compose.onNodeWithText("PlayStation App").performClick()

        assertEquals(0, addReplay)
        assertEquals(1, playStation)
    }

    @Test
    fun `offers to add a replay when there is none`() {
        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(lap, null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("No replay").assertExists()
        compose.onNodeWithText("Add a replay").assertExists()
    }

    @Test
    fun `offers to resume a pending sync`() {
        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(lap.copy(replayPending = true), null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("Sync pending").assertExists()
        compose.onNodeWithText("Resume sync").assertExists()
    }

    @Test
    fun `offers to replace a saved replay`() {
        compose.setContent {
            PocketSimEngineerTheme {
                EditLapSheet(lap.copy(hasReplay = true), null, null, emptyList(), null, {}, {}, {}, {}, {}, {})
            }
        }

        compose.onNodeWithText("Replace replay").assertExists()
    }
}
