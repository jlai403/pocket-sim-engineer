package io.github.jlai403.pocketsimengineer.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.jlai403.pocketsimengineer.app.replay.PlayStationApp
import io.github.jlai403.pocketsimengineer.app.share.Sharing
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.playback.PlaybackCursor
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
    onImportSync: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    val settings = remember { SettingsStore(context) }
    fun openPlayStationApp() {
        if (!PlayStationApp.open(context)) {
            Toast.makeText(context, "PlayStation App is not available.", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportSync(uri.toString())
    }

    var sessionReload by remember { mutableIntStateOf(0) }
    val session by produceState<SessionMetadata?>(null, sessionId, sessionReload) {
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
    var message by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                positionMs = newPosition.positionMs
            }

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

    LaunchedEffect(videoFile?.absolutePath) {
        val file = videoFile ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player, playing) {
        while (playing) {
            positionMs = player.currentPosition
            delay(50)
        }
    }

    val cursorMs = lap?.let {
        PlaybackCursor.telemetryMs(positionMs, it.videoStartOffsetMs, it.syncOffsetMs, it.lapTimeMs)
    } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ScreenHeader(
            title = lap?.let { "Lap ${it.index} · ${lapTimeLabel(it.lapTimeMs)}" } ?: "Lap",
            subtitle = session?.trackLabel,
            onBack = onBack,
            actions = {
                GhostPill("Share", onClick = {
                    val file = videoFile
                    if (file != null) {
                        Sharing.shareFile(context, file, "video/mp4", "Share lap $lapIndex")
                    } else {
                        message = "No video yet"
                    }
                })
                GhostPill("CSV", onClick = {
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
                })
            },
        )

        if (lap == null) {
            Text(
                "Loading lap…",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (videoFile != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black),
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostPill(
                        "Replace replay",
                        onClick = { importLauncher.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f),
                    )
                    GhostPill("PS App", onClick = { openPlayStationApp() })
                    if (lap.sourceFile != null) {
                        GhostPill(
                            "Resume import",
                            onClick = { onImportSync(null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (lap.sourceFile != null) {
                    Text(
                        "A newer clip is waiting to be synced. The current replay still plays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SectionCard(title = "Replay") {
                    if (lap.sourceFile != null) {
                        Text(
                            "Replay imported — sync not finished",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostPill(
                                "Resume sync",
                                onClick = { onImportSync(null) },
                                modifier = Modifier.weight(1f),
                            )
                            GhostPill(
                                "Choose another clip",
                                onClick = { importLauncher.launch(arrayOf("video/*")) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Text("No replay attached", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Record the single-lap replay on the PS5, upload it to the PS App, " +
                                "then add it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryPill(
                                text = "Add replay",
                                onClick = { importLauncher.launch(arrayOf("video/*")) },
                                modifier = Modifier.weight(1f),
                            )
                            GhostPill("PS App", onClick = { openPlayStationApp() })
                        }
                    }
                }
            }

            DriverSection(
                sessionId = sessionId,
                lapIndex = lapIndex,
                driverPsnId = lap.driverPsnId,
                onChanged = { sessionReload++ },
            )

            NumericText(
                "%s / %s".format(lapTimeLabel(cursorMs), lapTimeLabel(lap.lapTimeMs)),
                style = MaterialTheme.typography.titleMedium,
            )
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = "Telemetry") {
                ChartColumn(
                    samples = samples,
                    lap = lap,
                    cursorMs = cursorMs,
                    imperial = settings.imperialUnits,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard {
                Text("Track map", style = MaterialTheme.typography.titleMedium)
                TrackMap(samples = samples, lap = lap, cursorMs = cursorMs)
            }

            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}
