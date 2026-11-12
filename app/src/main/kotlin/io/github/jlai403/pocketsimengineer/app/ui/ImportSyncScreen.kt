package io.github.jlai403.pocketsimengineer.app.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
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
    var renderedMs by remember { mutableLongStateOf(-1L) }
    var anchorMs by remember { mutableStateOf<Long?>(null) }
    var playing by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var hadClip by remember { mutableStateOf(false) }

    fun seekTo(targetMs: Long) {
        val clamped = targetMs.coerceIn(0, durationMs.coerceAtLeast(0))
        player.seekTo(clamped)
        positionMs = clamped
        renderedMs = -1
        player.pause()
        previewing = false
    }

    BackHandler { confirmCancel = true }

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
        val session = withContext(Dispatchers.IO) { store.load(sessionId) }
        val loaded = session?.laps?.firstOrNull { it.index == lapIndex }
        if (session == null || loaded == null) {
            error = "Lap not found."
            return@LaunchedEffect
        }
        hadClip = loaded.videoFile.isNotBlank() &&
            !loaded.needsCut &&
            loaded.videoFile != session.rawFile
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
            // Paused seeks keep the exact position we set; polling would race the frame steps.
            if (player.isPlaying) positionMs = player.currentPosition
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

    val currentLap = lap
    val anchor = anchorMs
    val displayedMs = if (renderedMs >= 0) renderedMs else positionMs
    val sync = if (currentLap != null && anchor != null) ClipSync(anchor, currentLap.lapTimeMs) else null
    val range = sync?.let { TrimPlan.forSync(it, durationMs) }
    val check = sync?.let { SyncValidator.check(it, durationMs, startTrackPercent = 0f) }
    val telemetryMs = sync?.telemetryMsAt(displayedMs)
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
                append(formatMillis(displayedMs))
                if (trackPercent != null) append(" · %.1f%% lap".format(trackPercent))
                if (nearestSpeed != null) append(" · %.0f km/h".format(nearestSpeed * 3.6f))
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Anchor: the finish-line frame that ends the lap",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = positionMs.coerceIn(0, durationMs).toFloat(),
            onValueChange = { seekTo(it.roundToLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            enabled = durationMs > 0,
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
                formatMillis(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        range?.let {
            TrimBar(startMs = it.startMs, endMs = it.endMs, durationMs = durationMs)
            Text(
                "Shaded range = the lap that will be saved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransportRow(
            playing = playing,
            enabled = durationMs > 0,
            onRewind = { seekTo(positionMs - REWIND_MS) },
            onPlayPause = {
                if (player.isPlaying) {
                    player.pause()
                    positionMs = player.currentPosition
                } else {
                    previewing = false
                    player.play()
                }
            },
            onForward = { seekTo(positionMs + REWIND_MS) },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JogWheel(
                frameStepMs = frameStepMs,
                enabled = durationMs > 0,
                onFrames = { frames -> seekTo(positionMs + frames * frameStepMs) },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepButton("−1 frame", { seekTo(positionMs - frameStepMs) }, Modifier.weight(1f))
                    StepButton("+1 frame", { seekTo(positionMs + frameStepMs) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepButton("−0.01 s", { seekTo(positionMs - 10) }, Modifier.weight(1f))
                    StepButton("+0.01 s", { seekTo(positionMs + 10) }, Modifier.weight(1f))
                }
            }
        }

        PrimaryPill(
            text = "Set anchor at this frame",
            onClick = { anchorMs = displayedMs },
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

        if (hadClip) {
            Text(
                "Saving replaces the current replay. Discard keeps it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                                error = if (hadClip) {
                                    "Trim failed. The current replay is unchanged; try again."
                                } else {
                                    "Trim failed. The replay stays attached; try again."
                                }
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
            text = {
                Text(
                    if (hadClip) {
                        "The copied clip is deleted. The current replay stays attached."
                    } else {
                        "The copied clip is deleted. The telemetry lap stays."
                    },
                )
            },
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

private const val REWIND_MS = 10_000L

@Composable
private fun TransportRow(
    playing: Boolean,
    enabled: Boolean,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(TransportIcons.Rewind, "Rewind 10 seconds", enabled, onRewind)
        TransportButton(
            icon = if (playing) TransportIcons.Pause else Icons.Filled.PlayArrow,
            description = if (playing) "Pause" else "Play",
            enabled = enabled,
            onClick = onPlayPause,
            primary = true,
        )
        TransportButton(TransportIcons.Forward, "Forward 10 seconds", enabled, onForward)
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(if (primary) 64.dp else 52.dp)
            .clip(CircleShape)
            .background(
                if (primary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(if (primary) 34.dp else 26.dp),
        )
    }
}

/** Fine steps need tighter padding than a full-width pill to keep the labels on one line. */
@Composable
private fun StepButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun TrimBar(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val lap = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        if (durationMs <= 0 || size.width <= 0f) return@Canvas
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        val left = (startMs.toFloat() / durationMs * size.width).coerceIn(0f, size.width)
        val right = (endMs.toFloat() / durationMs * size.width).coerceIn(0f, size.width)
        if (right - left >= 1f) {
            drawRoundRect(
                color = lap,
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height),
                cornerRadius = radius,
            )
        }
        drawCircle(
            color = marker,
            radius = size.height / 2.2f,
            center = Offset(right, size.height / 2f),
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
