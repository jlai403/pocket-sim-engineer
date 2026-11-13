package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import io.github.jlai403.lapcraft.core.session.LapRef
import io.github.jlai403.lapcraft.core.session.LapSummary

/** Picks which laps go into a library archive. Everything is selected by default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportLapsSheet(
    laps: List<LapSummary>,
    onDismiss: () -> Unit,
    onExport: (List<LapRef>) -> Unit,
) {
    var selected by remember(laps) {
        mutableStateOf(laps.map { LapRef(it.sessionId, it.lapIndex) }.toSet())
    }
    val selectedLaps = laps.filter { LapRef(it.sessionId, it.lapIndex) in selected }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Export laps",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                GhostPill(
                    text = if (selected.size == laps.size) "Clear" else "Select all",
                    onClick = {
                        selected = if (selected.size == laps.size) {
                            emptySet()
                        } else {
                            laps.map { LapRef(it.sessionId, it.lapIndex) }.toSet()
                        }
                    },
                    modifier = Modifier.testTag("export-laps-select-all"),
                )
            }
            Text(
                "${selected.size} of ${laps.size} selected · ≈ ${formatBytes(exportBytes(selectedLaps))}",
                modifier = Modifier.testTag("export-laps-summary"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(laps, key = { "${it.sessionId}-${it.lapIndex}" }) { lap ->
                    val ref = LapRef(lap.sessionId, lap.lapIndex)
                    val checked = ref in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export-lap-${lap.sessionId}-${lap.lapIndex}")
                            .clickable {
                                selected = if (checked) selected - ref else selected + ref
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${lap.trackLabel} · ${lapTimeLabel(lap.lapTimeMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${shortDateLabel(lap.createdAt)} · ${formatBytes(lapBytes(lap))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Text(
                "Clips and telemetry travel with each lap. Settings and your driver profile stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryPill(
                text = if (selected.isEmpty()) {
                    "Export"
                } else {
                    "Export ${selected.size} lap${if (selected.size == 1) "" else "s"}"
                },
                onClick = { onExport(selectedLaps.map { LapRef(it.sessionId, it.lapIndex) }) },
                modifier = Modifier.fillMaxWidth().testTag("export-laps-button"),
                enabled = selected.isNotEmpty(),
            )
        }
    }
}

internal fun lapBytes(lap: LapSummary): Long = lap.videoBytes + lap.telemetryBytes + lap.pendingBytes

internal fun exportBytes(laps: Collection<LapSummary>): Long = laps.sumOf { lapBytes(it) }
