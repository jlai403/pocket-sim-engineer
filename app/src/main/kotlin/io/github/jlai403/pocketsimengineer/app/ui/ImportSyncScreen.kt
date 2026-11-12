package io.github.jlai403.pocketsimengineer.app.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.jlai403.pocketsimengineer.app.replay.ClipTrimmer
import io.github.jlai403.pocketsimengineer.app.replay.ReplayStore
import io.github.jlai403.pocketsimengineer.core.clip.ClipSync
import io.github.jlai403.pocketsimengineer.core.clip.SyncCheck
import io.github.jlai403.pocketsimengineer.core.clip.SyncValidator
import io.github.jlai403.pocketsimengineer.core.clip.TrackProgress
import io.github.jlai403.pocketsimengineer.core.clip.TrimPlan
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
@Composable
fun ImportSyncScreen(
    sessionId: String,
    lapIndex: Int,
    sourceUri: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    val trimmer = remember { ClipTrimmer() }
    val player = remember(sessionId, lapIndex) {
        ExoPlayer.Builder(context).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    var lap by remember { mutableStateOf<LapMetadata?>(null) }
    var samples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var sourceFile by remember { mutableStateOf<File?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var frameStepMs by remember { mutableLongStateOf(33L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var renderedMs by remember { mutableLongStateOf(0L) }
    var anchorMs by remember { mutableStateOf<Long?>(null) }
    var playing by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                error = "Playback failed: ${playbackError.message}"
            }
        }
        player.addListener(listener)
        player.setVideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
            renderedMs = presentationTimeUs / 1_000
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(sessionId, lapIndex, sourceUri) {
        error = null
        val loaded = withContext(Dispatchers.IO) {
            store.load(sessionId)?.laps?.firstOrNull { it.index == lapIndex }
        }
        if (loaded == null) {
            error = "Lap not found."
            return@LaunchedEffect
        }
        lap = loaded
        samples = withContext(Dispatchers.IO) {
            runCatching { store.readSamples(sessionId, lapIndex) }.getOrDefault(emptyList())
        }

        val file = if (sourceUri != null) {
            val destination = ReplayStore.sourceFile(store.sessionDir(sessionId), lapIndex)
            val copied = withContext(Dispatchers.IO) {
                ReplayStore.copy(context, Uri.parse(sourceUri), destination)
            }
            if (!copied) {
                error = "Could not read the replay. Download it in the PS App and pick the file instead."
                return@LaunchedEffect
            }
            val relative = "laps/lap-%03d-source.mp4".format(lapIndex)
            withContext(Dispatchers.IO) { store.setSource(sessionId, lapIndex, relative) }
            destination
        } else {
            loaded.sourceFile?.let { File(store.sessionDir(sessionId), it) }
        }

        if (file == null || !file.isFile) {
            error = "No replay file attached."
            return@LaunchedEffect
        }
        sourceFile = file
        val probe = withContext(Dispatchers.IO) { probe(file) }
        durationMs = probe.first
        frameStepMs = probe.second
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.duration > 0) durationMs = maxOf(durationMs, player.duration)
            positionMs = player.currentPosition
            delay(50)
        }
    }

    LaunchedEffect(previewing) {
        val range = trimRange(lap, anchorMs, durationMs)
        if (!previewing || range == null) {
            return@LaunchedEffect
        }
        player.seekTo(range.startMs)
        player.play()
        while (previewing) {
            if (player.currentPosition >= range.endMs) player.seekTo(range.startMs)
            delay(50)
        }
    }

    fun seekTo(targetMs: Long) {
        val clamped = targetMs.coerceIn(0, durationMs)
        player.seekTo(clamped)
        positionMs = clamped
        renderedMs = clamped
        player.pause()
        previewing = false
    }

    val currentLap = lap
    val anchor = anchorMs
    val sync = if (currentLap != null && anchor != null) ClipSync(anchor, currentLap.lapTimeMs) else null
    val range = sync?.let { TrimPlan.forSync(it, durationMs) }
    val check = sync?.let { SyncValidator.check(it, durationMs, startTrackPercent = 0f) }
    val telemetryMs = sync?.telemetryMsAt(renderedMs)
    val trackPercent = if (currentLap != null && telemetryMs != null) {
        TrackProgress.percentAt(samples, currentLap.startNanos, telemetryMs)
    } else {
        null
    }
    val nearestSpeed = if (currentLap != null && telemetryMs != null) {
        val target = currentLap.startNanos + telemetryMs * 1_000_000L
        samples.minByOrNull { abs(it.timeNanos - target) }?.speedMps
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Sync replay",
            subtitle = currentLap?.let { "Lap ${it.index} · ${lapTimeLabel(it.lapTimeMs)}" } ?: "Replay",
            onBack = { confirmCancel = true },
        )

        val outerPlayer = player
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    setPlayer(outerPlayer)
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Text(
            buildString {
                append(formatMillis(renderedMs))
                if (trackPercent != null) append(" · %.1f%% lap".format(trackPercent))
                if (nearestSpeed != null) append(" · %.0f km/h".format(nearestSpeed * 3.6f))
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostPill("−1f", { seekTo(ImportSyncState(durationMs, positionMs, renderedMs, anchor, frameStepMs).stepFrame(false)) }, Modifier.weight(1f))
            GhostPill("−100", { seekTo(positionMs - 100) }, Modifier.weight(1f))
            GhostPill("−10", { seekTo(positionMs - 10) }, Modifier.weight(1f))
            GhostPill("−1", { seekTo(positionMs - 1) }, Modifier.weight(1f))
            GhostPill(
                if (playing) "Pause" else "Play",
                {
                    if (player.isPlaying) player.pause() else player.play()
                },
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GhostPill("+1", { seekTo(positionMs + 1) }, Modifier.weight(1f))
            GhostPill("+10", { seekTo(positionMs + 10) }, Modifier.weight(1f))
            GhostPill("+100", { seekTo(positionMs + 100) }, Modifier.weight(1f))
            GhostPill(
                "+1f",
                { seekTo(ImportSyncState(durationMs, positionMs, renderedMs, anchor, frameStepMs).stepFrame(true)) },
                Modifier.weight(1f),
            )
        }

        PrimaryPill(
            text = "Set anchor at this frame",
            onClick = { anchorMs = if (renderedMs > 0) renderedMs else positionMs },
            modifier = Modifier.fillMaxWidth(),
            enabled = durationMs > 0,
        )

        SectionCard(title = "Trim") {
            if (sync == null) {
                Text(
                    "Frame-step to the lap-end line, then set the anchor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MetricLine("Clip in", formatMillis(sync.trimInMs))
                MetricLine("Clip out", formatMillis(sync.trimOutMs))
                MetricLine("Lap", lapTimeLabel(sync.lapTimeMs))
                range?.let { MetricLine("Start frame", formatMillis(it.startMs)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostPill("Jump to start", {
                        range?.let {
                            seekTo(it.startMs)
                        }
                    }, Modifier.weight(1f))
                    GhostPill(
                        if (previewing) "Stop preview" else "Preview",
                        {
                            if (previewing) {
                                previewing = false
                                player.pause()
                            } else if (range != null) {
                                previewing = true
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
                when (val result = check) {
                    is SyncCheck.ClipTooShort -> Text(
                        "Clip is ${result.missingMs} ms too short. Record a few seconds more before the lap.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is SyncCheck.StartNotAtLine -> Text(
                        "Computed start is %.1f%% around the lap. Re-anchor at the line.".format(result.trackPercent),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> Text(
                        "Verify the start frame shows the line, then save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostPill("Discard", { confirmCancel = true }, Modifier.weight(1f))
            PrimaryPill(
                text = if (saving) "Saving…" else "Save clip",
                onClick = {
                    val trimRange = range
                    val source = sourceFile
                    if (trimRange != null && source != null && !saving) {
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val output = File(
                                    store.sessionDir(sessionId),
                                    "laps/lap-%03d.mp4".format(lapIndex),
                                )
                                val trimmed = trimmer.trim(source, output, trimRange)
                                    ?: return@withContext null
                                store.attachClip(
                                    sessionId = sessionId,
                                    lapIndex = lapIndex,
                                    videoFile = "laps/lap-%03d.mp4".format(lapIndex),
                                    videoStartOffsetMs = trimmed.startOffsetMs,
                                )
                                trimmed
                            }
                            saving = false
                            if (result == null) {
                                error = "Trim failed. The replay stays attached; try again."
                            } else {
                                onDone()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = check == SyncCheck.Ok && !saving,
            )
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Discard this replay?") },
            text = { Text("The copied clip is deleted. The telemetry lap stays.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            store.setSource(sessionId, lapIndex, null)
                            sourceFile?.delete()
                        }
                        onCancel()
                    }
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumericText(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun trimRange(lap: LapMetadata?, anchorMs: Long?, durationMs: Long) =
    if (lap != null && anchorMs != null) {
        TrimPlan.forSync(ClipSync(anchorMs, lap.lapTimeMs), durationMs)
    } else {
        null
    }

private fun formatMillis(ms: Long): String {
    if (ms < 0) return "-"
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1_000
    val millis = ms % 1_000
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}

private fun probe(file: File): Pair<Long, Long> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val duration = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val captureFps = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
        val frames = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            ?.toLongOrNull()
        val frameMs = when {
            captureFps != null && captureFps > 0f -> (1_000f / captureFps).roundToLong()
            frames != null && frames > 0 && duration > 0 -> duration / frames
            else -> 33L
        }.coerceAtLeast(1L)
        duration to frameMs
    } catch (_: Exception) {
        0L to 33L
    } finally {
        runCatching { retriever.release() }
    }
}
