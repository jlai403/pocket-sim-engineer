package io.github.jlai403.pocketsimengineer.app.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.jlai403.pocketsimengineer.app.share.Sharing
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.playback.PlaybackCursor
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun LapPlayerScreen(
    sessionId: String,
    lapIndex: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    val settings = remember { SettingsStore(context) }

    val session by produceState<SessionMetadata?>(null, sessionId) {
        value = withContext(Dispatchers.IO) { store.load(sessionId) }
    }
    val lap = session?.laps?.firstOrNull { it.index == lapIndex }
    val samples by produceState<List<Sample>>(emptyList(), sessionId, lapIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching { store.readSamples(sessionId, lapIndex) }.getOrDefault(emptyList())
        }
    }

    val videoFile = lap
        ?.takeIf { !it.needsCut && it.videoFile != session?.rawFile }
        ?.let { File(store.sessionDir(sessionId), it.videoFile) }
        ?.takeIf { it.isFile }
    val player = remember(sessionId, lapIndex) { ExoPlayer.Builder(context).build() }

    var positionMs by remember { mutableLongStateOf(0L) }
    var syncOffset by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                message = "Playback failed: ${error.message}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(session) { session?.let { syncOffset = it.syncOffsetMs } }

    LaunchedEffect(videoFile?.absolutePath) {
        val file = videoFile ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            delay(50)
        }
    }

    val cursorMs = lap?.let {
        PlaybackCursor.telemetryMs(positionMs, it.videoStartOffsetMs, syncOffset, it.lapTimeMs)
    } ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                lap?.let { "Lap ${it.index} · ${lapTimeLabel(it.lapTimeMs)}" } ?: "Lap",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val file = videoFile
                if (file != null) {
                    Sharing.shareFile(context, file, "video/mp4", "Share lap $lapIndex")
                } else {
                    message = "No video yet"
                }
            }) { Text("Share") }
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            Sharing.exportCsv(context, store, sessionId, lap ?: error("no lap"))
                        }
                    }.onSuccess {
                        Sharing.shareFile(context, it, "text/csv", "Export lap $lapIndex")
                    }.onFailure {
                        message = "CSV export failed: ${it.message}"
                    }
                }
            }) { Text("CSV") }
        }

        if (lap == null) {
            Text("Loading lap…", modifier = Modifier.padding(16.dp))
            return@Column
        }
        if (videoFile == null) {
            Text(
                "Lap video is not available yet. The raw recording is kept until remux succeeds.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )

            Text(
                "%s / %s  ·  sync %+d ms".format(
                    lapTimeLabel(cursorMs),
                    lapTimeLabel(lap.lapTimeMs),
                    syncOffset,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { syncOffset = (syncOffset - 500).coerceIn(-500, 500) }) { Text("-500") }
                TextButton(onClick = { syncOffset = (syncOffset - 50).coerceIn(-500, 500) }) { Text("-50") }
                TextButton(onClick = { syncOffset = 0 }) { Text("Reset") }
                TextButton(onClick = { syncOffset = (syncOffset + 50).coerceIn(-500, 500) }) { Text("+50") }
                TextButton(onClick = { syncOffset = (syncOffset + 500).coerceIn(-500, 500) }) { Text("+500") }
            }

            TextButton(onClick = {
                val updated = syncOffset.coerceIn(-500, 500)
                syncOffset = updated
                scope.launch {
                    withContext(Dispatchers.IO) { store.setSyncOffset(sessionId, updated) }
                }
            }) { Text("Save sync offset") }

            ChartColumn(
                samples = samples,
                lap = lap,
                cursorMs = cursorMs,
                imperial = settings.imperialUnits,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Track map", style = MaterialTheme.typography.titleMedium)
            TrackMap(samples = samples, lap = lap, cursorMs = cursorMs)

            Spacer(Modifier.padding(8.dp))
        }
    }
}
