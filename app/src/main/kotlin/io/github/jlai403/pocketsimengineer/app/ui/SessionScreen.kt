package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
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
    var confirmingDelete by remember { mutableStateOf(false) }
    var trackName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        session = withContext(Dispatchers.IO) { store.load(sessionId) }
        trackName = session?.trackLabel.orEmpty()
    }

    val current = session
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = current?.trackLabel ?: "Session",
            subtitle = current?.carName,
            onBack = onBack,
            actions = {
                GhostPill("Rename", onClick = { renaming = true })
                GhostPill(
                    "Delete",
                    onClick = { confirmingDelete = true },
                    contentColor = MaterialTheme.colorScheme.error,
                )
            },
        )

        if (current == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 32.dp),
            )
            return@Column
        }

        val best = current.laps.minOfOrNull { it.lapTimeMs }
        Text(
            "${current.laps.size} laps" + (best?.let { " · best ${lapTimeLabel(it)}" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Recorded ${formatDate(current.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete session?") },
            text = { Text("This session and all its laps will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(sessionId) }
                        onDeleted()
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Lap ${lap.index}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                NumericText(lapTimeLabel(lap.lapTimeMs), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${lap.sampleCount} samples",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lap.needsCut) {
                    Text(
                        "Video not cut yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            PrimaryPill("Open", onOpen)
        }
        Spacer(Modifier.padding(vertical = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostPill("Share MP4", onShare)
            GhostPill("Export CSV", onCsv)
        }
    }
}
