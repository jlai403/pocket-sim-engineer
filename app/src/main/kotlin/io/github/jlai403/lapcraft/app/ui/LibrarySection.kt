package io.github.jlai403.lapcraft.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.app.share.LibraryTransfer
import io.github.jlai403.lapcraft.core.session.ExportReport
import io.github.jlai403.lapcraft.core.session.ImportReport
import io.github.jlai403.lapcraft.core.session.LapRef
import io.github.jlai403.lapcraft.core.session.LapSummary
import io.github.jlai403.lapcraft.core.session.SessionStore
import java.time.LocalDate
import kotlinx.coroutines.launch

private sealed interface Transfer {
    data object Idle : Transfer
    data class Busy(val label: String, val done: Long, val total: Long) : Transfer
    data class Done(val text: String) : Transfer
    data class Failed(val text: String) : Transfer
}

/** Backup and restore for the whole library, used to carry laps across a package change. */
@Composable
internal fun LibrarySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = context.filesDir
    val store = remember { SessionStore(root) }
    var laps by remember { mutableStateOf<List<LapSummary>>(store.summaries()) }
    var pending by remember { mutableStateOf<List<LapRef>>(emptyList()) }
    var sheet by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<Transfer>(Transfer.Idle) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val refs = pending
            transfer = Transfer.Busy("Exporting", 0, 0)
            scope.launch {
                runCatching {
                    LibraryTransfer.export(context, uri, root, refs) { done, total ->
                        transfer = Transfer.Busy("Exporting", done, total)
                    }
                }
                    .onSuccess { transfer = Transfer.Done(exportSummary(it)) }
                    .onFailure { transfer = Transfer.Failed(it.message ?: "Export failed") }
                laps = store.summaries()
                pending = emptyList()
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            transfer = Transfer.Busy("Importing", 0, 0)
            scope.launch {
                runCatching {
                    LibraryTransfer.import(context, uri, root) { done, total ->
                        transfer = Transfer.Busy("Importing", done, total)
                    }
                }
                    .onSuccess { transfer = Transfer.Done(importSummary(it)) }
                    .onFailure { transfer = Transfer.Failed(it.message ?: "Import failed") }
                laps = store.summaries()
            }
        }
    }

    SectionCard(title = "Library") {
        Text(
            "${laps.size} laps · ${formatBytes(exportBytes(laps))}",
            modifier = Modifier.testTag("settings-library-summary"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostPill(
                text = "Export laps…",
                onClick = { sheet = true },
                modifier = Modifier.testTag("settings-library-export"),
                enabled = laps.isNotEmpty() && transfer is Transfer.Idle,
            )
            GhostPill(
                text = "Import library…",
                onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.testTag("settings-library-import"),
                enabled = transfer is Transfer.Idle,
            )
        }
        when (val state = transfer) {
            is Transfer.Idle -> Unit
            is Transfer.Busy -> {
                Text(
                    "${state.label}…",
                    modifier = Modifier.testTag("settings-library-status"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            is Transfer.Done -> Text(
                state.text,
                modifier = Modifier.testTag("settings-library-status"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is Transfer.Failed -> Text(
                state.text,
                modifier = Modifier.testTag("settings-library-status"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "A backup keeps clips and telemetry per lap. Settings and your driver profile stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (sheet) {
        ExportLapsSheet(
            laps = laps,
            onDismiss = { sheet = false },
            onExport = { refs ->
                pending = refs
                sheet = false
                exporter.launch("lapcraft-library-${LocalDate.now()}.zip")
            },
        )
    }
}

internal fun importSummary(report: ImportReport): String = buildString {
    append("Imported ${report.laps} lap${if (report.laps == 1) "" else "s"}")
    if (report.skipped > 0) append(", skipped ${report.skipped} already here")
    if (report.failed > 0) append(", ${report.failed} could not be read")
    append(".")
}

internal fun exportSummary(report: ExportReport): String =
    if (report.laps == 0) "Nothing to export." else "Exported ${report.laps} lap${if (report.laps == 1) "" else "s"}."
