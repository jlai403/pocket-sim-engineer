package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.GridStatsProfileClient
import io.github.jlai403.pocketsimengineer.app.track.TrackNamer
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import io.github.jlai403.pocketsimengineer.core.track.TrackCandidate

/** Track, driver, and replay for one lap, without leaving the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditLapSheet(
    lap: LapSummary,
    driver: DriverProfile?,
    owner: DriverProfile?,
    profiles: List<DriverProfile>,
    candidates: List<TrackCandidate>?,
    onDismiss: () -> Unit,
    onSaveTrack: (String) -> Unit,
    onAssignDriver: (DriverProfile?) -> Unit,
    onAddReplay: () -> Unit,
    onOpenPlayStation: () -> Unit,
    onResumeSync: () -> Unit,
    developerTools: Boolean = false,
    reencoding: Boolean = false,
    reencodeResult: Boolean? = null,
    onReencode: () -> Unit = {},
) {
    var track by remember(lap.trackLabel) {
        mutableStateOf(lap.trackLabel.takeUnless { it == TrackNamer.UNKNOWN }.orEmpty())
    }
    var driverPsnId by remember(lap.driverPsnId) { mutableStateOf(lap.driverPsnId) }
    var localProfiles by remember(profiles) { mutableStateOf(profiles) }
    var addingDriver by remember { mutableStateOf(false) }
    val selectedDriver = localProfiles.firstOrNull { it.psnId.equals(driverPsnId, true) }
        ?: driver?.takeIf { it.psnId.equals(driverPsnId, true) }
        ?: owner?.takeIf { it.psnId.equals(driverPsnId, true) }

    fun assign(profile: DriverProfile?) {
        driverPsnId = profile?.psnId
        if (profile != null && localProfiles.none { it.psnId.equals(profile.psnId, true) }) {
            localProfiles = localProfiles + profile
        }
        onAssignDriver(profile)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit lap", style = MaterialTheme.typography.headlineMedium)

            SectionLabel("Track")
            OutlinedTextField(
                value = track,
                onValueChange = { track = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("edit-track"),
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Track name") },
            )
            if (lap.trackLabel == TrackNamer.UNKNOWN) {
                when {
                    candidates == null -> Text(
                        "Finding tracks…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> candidates.forEach { candidate ->
                        Text(
                            candidate.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { track = candidate.name }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            SectionLabel("Driver")
            DriverPickerRow(
                driverPsnId = driverPsnId,
                profile = selectedDriver,
                owner = owner,
                profiles = localProfiles,
                onChange = ::assign,
                onAddDriver = { addingDriver = true },
                onOpenLink = null,
            )

            SectionLabel("Replay")
            Text(
                when {
                    lap.replayPending -> "Sync pending"
                    lap.hasReplay -> "Saved"
                    else -> "No replay"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lap.replayPending) {
                    GhostPill(
                        text = "Resume sync",
                        onClick = {
                            onDismiss()
                            onResumeSync()
                        },
                    )
                }
                ReplaySourcePill(
                    text = if (lap.hasReplay || lap.replayPending) "Replace replay" else "Add a replay",
                    primary = false,
                    onFiles = {
                        onDismiss()
                        onAddReplay()
                    },
                    onPlayStation = {
                        onDismiss()
                        onOpenPlayStation()
                    },
                )
            }
            if (developerTools && lap.hasReplay) {
                if (reencoding) {
                    Row(
                        modifier = Modifier.testTag("reencode-progress"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Re-encoding…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    GhostPill(
                        text = "Re-encode clip",
                        onClick = onReencode,
                    )
                }
                Text(
                    when (reencodeResult) {
                        true -> "Re-encoded with 0.1 s keyframes."
                        false -> "Re-encode failed. The clip is unchanged."
                        null -> "Rebuilds the clip with a 0.1 s keyframe interval so exact frame seeking is cheap."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reencodeResult == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            SectionLabel("Data")
            Text(
                formatBytes(lap.videoBytes + lap.telemetryBytes + lap.pendingBytes) + " total",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    lap.videoBytes.takeIf { it > 0 }?.let { "Replay ${formatBytes(it)}" },
                    lap.pendingBytes.takeIf { it > 0 }?.let { "Pending clip ${formatBytes(it)}" },
                    "Telemetry ${formatBytes(lap.telemetryBytes)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrimaryPill(
                text = "Save",
                onClick = { onSaveTrack(track.trim().ifBlank { TrackNamer.UNKNOWN }) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (addingDriver) {
        AddDriverDialog(
            lookupProfile = { GridStatsProfileClient().fetch(it) },
            onSave = {
                addingDriver = false
                assign(it)
            },
            onDismiss = { addingDriver = false },
        )
    }
}
