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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Alignment
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
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import io.github.jlai403.pocketsimengineer.app.replay.PlayStationApp
import io.github.jlai403.pocketsimengineer.app.replay.probeClip
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
import kotlin.math.roundToLong

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
    val player = remember(sessionId, lapIndex) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var renderedUs by remember { mutableLongStateOf(-1L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var frameStepMs by remember { mutableLongStateOf(33L) }
    var frameDurationUs by remember { mutableLongStateOf(33_000L) }
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
        player.setVideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
            renderedUs = presentationTimeUs
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(videoFile?.absolutePath) {
        val file = videoFile ?: return@LaunchedEffect
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        durationMs = probe.durationMs
        frameStepMs = probe.frameMs
        frameDurationUs = probe.frameDurationUs
        val start = lap?.let {
            PlaybackCursor.clipMs(0, it.leadInMs, it.syncOffsetMs)
        } ?: 0L
        val ready = start.coerceIn(0L, probe.durationMs.coerceAtLeast(0L))
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.seekTo(ready)
            player.prepare()
            player.playWhenReady = true
            positionMs = ready
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.duration > 0) durationMs = maxOf(durationMs, player.duration)
            if (player.isPlaying) positionMs = player.currentPosition
            delay(50)
        }
    }

    fun seekClip(targetMs: Long) {
        val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(clamped)
        positionMs = clamped
        renderedUs = -1L
        player.pause()
    }

    fun seekLap(targetMs: Long) {
        val active = lap ?: return
        val clamped = targetMs.coerceIn(0L, active.lapTimeMs.toLong())
        seekClip(PlaybackCursor.clipMs(clamped.toInt(), active.leadInMs, active.syncOffsetMs))
    }

    val displayedUs = if (renderedUs >= 0) renderedUs else positionMs * 1_000
    val displayedMs = if (renderedUs >= 0) renderedUs / 1_000 else positionMs
    val cursorMs = lap?.let {
        PlaybackCursor.telemetryMs(displayedMs, it.leadInMs, it.syncOffsetMs, it.lapTimeMs)
    } ?: 0

    fun stepFrames(frames: Int) {
        val active = lap ?: return
        val startMs = PlaybackCursor.clipMs(0, active.leadInMs, active.syncOffsetMs)
            .coerceAtLeast(0)
        val endMs = PlaybackCursor.clipMs(active.lapTimeMs, active.leadInMs, active.syncOffsetMs)
            .coerceAtMost(durationMs.coerceAtLeast(0))
        val targetMs = frameStepTargetMs(displayedUs, frames, frameDurationUs)
            .coerceIn(startMs, endMs.coerceAtLeast(startMs))
        seekClip(targetMs)
    }

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
        val currentLap = lap

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
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                }
                if (currentLap.sourceFile != null) {
                    PrimaryPill(
                        text = "Resume sync",
                        onClick = { onImportSync(null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ReplaySourcePill(
                    text = "Replace replay",
                    primary = false,
                    onFiles = { importLauncher.launch(arrayOf("video/*")) },
                    onPlayStation = { openPlayStationApp() },
                )
                if (currentLap.sourceFile != null) {
                    Text(
                        "A newer clip is waiting to be synced. The current replay still plays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SectionCard(title = "Replay") {
                    if (currentLap.sourceFile != null) {
                        Text(
                            "Clip imported — sync not finished",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        PrimaryPill(
                            text = "Resume sync",
                            onClick = { onImportSync(null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ReplaySourcePill(
                            text = "Choose another clip",
                            primary = false,
                            onFiles = { importLauncher.launch(arrayOf("video/*")) },
                            onPlayStation = { openPlayStationApp() },
                        )
                    } else {
                        Text("No replay attached", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Record the single-lap replay on the PS5, upload it to the PS App, " +
                                "then add it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReplaySourcePill(
                            text = "Add replay",
                            primary = true,
                            onFiles = { importLauncher.launch(arrayOf("video/*")) },
                            onPlayStation = { openPlayStationApp() },
                        )
                    }
                }
            }

            if (videoFile != null) {
                NumericText(
                    "%s / %s".format(
                        formatMillis(cursorMs.toLong()),
                        lapTimeLabel(currentLap.lapTimeMs),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = cursorMs.toFloat().coerceIn(0f, currentLap.lapTimeMs.toFloat()),
                    onValueChange = { seekLap(it.roundToLong()) },
                    valueRange = 0f..currentLap.lapTimeMs.coerceAtLeast(1).toFloat(),
                    enabled = durationMs > 0 && currentLap.lapTimeMs > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "0:00.000",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        lapTimeLabel(currentLap.lapTimeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FrameRuler(
                    valueMs = cursorMs.toLong(),
                    startMs = 0,
                    endMs = currentLap.lapTimeMs.toLong(),
                    frameStepMs = frameStepMs,
                    enabled = durationMs > 0,
                    onFrames = { frames -> stepFrames(frames) },
                )
                Text(
                    "Frame ruler · drag to step one frame",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TransportRow(
                    playing = playing,
                    enabled = durationMs > 0,
                    onRewind = { seekLap(cursorMs - TRANSPORT_MS) },
                    onPlayPause = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (cursorMs >= currentLap.lapTimeMs) seekLap(0)
                            player.play()
                        }
                    },
                    onForward = { seekLap(cursorMs + TRANSPORT_MS) },
                )
                FrameStepRow(enabled = durationMs > 0, onStep = ::stepFrames)
            }

            DriverSection(
                sessionId = sessionId,
                lapIndex = lapIndex,
                driverPsnId = currentLap.driverPsnId,
                onChanged = { sessionReload++ },
            )

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = "Telemetry") {
                ChartColumn(
                    samples = samples,
                    lap = currentLap,
                    cursorMs = cursorMs,
                    imperial = settings.imperialUnits,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard {
                Text("Track map", style = MaterialTheme.typography.titleMedium)
                TrackMap(samples = samples, lap = currentLap, cursorMs = cursorMs)
            }

            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

