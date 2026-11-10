package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.share.Sharing
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenLap: (Int) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    var session by remember { mutableStateOf<SessionMetadata?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var trackName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        session = withContext(Dispatchers.IO) { store.load(sessionId) }
        trackName = session?.trackLabel.orEmpty()
    }

    val current = session
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { renaming = true }) { Text("Rename") }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(sessionId) }
                        onDeleted()
                    }
                }) { Text("Delete") }
            }
        },
    ) { padding ->
        if (current == null) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(current.trackLabel, style = MaterialTheme.typography.headlineMedium)
            Text(current.carName, style = MaterialTheme.typography.titleMedium)
            Text("Recorded ${current.createdAt}", style = MaterialTheme.typography.bodySmall)
            val best = current.laps.minOfOrNull { it.lapTimeMs }
            Text(
                "${current.laps.size} laps" + (best?.let { " · best ${lapTimeLabel(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current.laps) { lap ->
                    LapCard(
                        lap = lap,
                        onOpen = { onOpenLap(lap.index) },
                        onShare = {
                            val file = File(store.sessionDir(sessionId), lap.videoFile)
                            if (file.isFile) {
                                Sharing.shareFile(context, file, "video/mp4", "Share lap ${lap.index}")
                            } else {
                                message = "Lap video is missing. Is remux still running?"
                            }
                        },
                        onCsv = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        Sharing.exportCsv(context, store, sessionId, lap)
                                    }
                                }.onSuccess {
                                    Sharing.shareFile(context, it, "text/csv", "Export lap ${lap.index}")
                                }.onFailure {
                                    message = "CSV export failed: ${it.message}"
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Track name") },
            text = {
                OutlinedTextField(
                    value = trackName,
                    onValueChange = { trackName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        val updated = withContext(Dispatchers.IO) {
                            store.rename(sessionId, trackName.trim().ifBlank { "Unknown" })
                        }
                        session = updated
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LapCard(
    lap: LapMetadata,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCsv: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lap ${lap.index}", style = MaterialTheme.typography.titleMedium)
                    Text(lapTimeLabel(lap.lapTimeMs), style = MaterialTheme.typography.bodyLarge)
                    Text("${lap.sampleCount} samples", style = MaterialTheme.typography.bodySmall)
                    if (lap.needsCut) {
                        Text("Video not cut yet", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onOpen) { Text("Open") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onShare) { Text("Share MP4") }
                TextButton(onClick = onCsv) { Text("Export CSV") }
            }
        }
    }
}
