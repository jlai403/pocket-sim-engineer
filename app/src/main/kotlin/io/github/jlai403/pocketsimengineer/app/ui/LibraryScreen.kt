package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.clickable
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
import io.github.jlai403.pocketsimengineer.core.session.SessionGroup
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(
    onOpenSession: (String) -> Unit,
    onCapture: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    var groups by remember { mutableStateOf<List<SessionGroup>?>(null) }
    var pendingDelete by remember { mutableStateOf<SessionMetadata?>(null) }

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) { store.groups() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCapture) { Text("Record") }
            TextButton(onClick = onSettings) { Text("Settings") }
        }

        val data = groups
        when {
            data == null -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            data.isEmpty() -> Text(
                "No sessions yet. Tap Record, arm capture, then drive a lap.",
                modifier = Modifier.padding(top = 24.dp),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(data) { group -> GroupCard(group, onOpenSession, onDelete = { pendingDelete = it }) }
            }
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("${session.trackLabel} on ${formatDate(session.createdAt)} and all its laps will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(session.sessionId) }
                        groups = withContext(Dispatchers.IO) { store.groups() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GroupCard(
    group: SessionGroup,
    onOpenSession: (String) -> Unit,
    onDelete: (SessionMetadata) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.trackLabel, style = MaterialTheme.typography.titleMedium)
            Text(group.carName, style = MaterialTheme.typography.bodySmall)
            for (session in group.sessions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(session.sessionId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatDate(session.createdAt), style = MaterialTheme.typography.bodyMedium)
                        val best = session.laps.minOfOrNull { it.lapTimeMs }
                        Text(
                            "${session.laps.size} laps" + (best?.let { " · best ${lapTimeLabel(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { onDelete(session) }) { Text("Delete") }
                }
            }
        }
    }
}

private fun formatDate(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
}.getOrDefault(iso)
