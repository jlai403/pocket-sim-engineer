package io.github.jlai403.lapcraft.app.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import io.github.jlai403.lapcraft.R
import io.github.jlai403.lapcraft.app.replay.probeClip
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.clip.TrackProgress
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.playback.PlaybackCursor
import io.github.jlai403.lapcraft.core.session.LapMetadata
import io.github.jlai403.lapcraft.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

/** One side of the comparison: its metadata, telemetry window, and clip when one is attached. */
private data class CompareLap(
    val sessionId: String,
    val lapIndex: Int,
    val trackLabel: String,
    val lap: LapMetadata,
    val samples: List<Sample>,
    val clip: File?,
)

/**
 * Two same-track laps side by side. The primary owns the clock; the reference is
 * seeked to the primary's track progress, frame-exact, and never plays.
 */
@OptIn(UnstableApi::class)
@Composable
fun LapCompareScreen(
    primarySessionId: String,
    primaryLapIndex: Int,
    referenceSessionId: String,
    referenceLapIndex: Int,
    onBack: () -> Unit,
    onAddClip: (String, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    val settings = remember { SettingsStore(context) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val primary by produceState<CompareLap?>(null, primarySessionId, primaryLapIndex) {
        value = loadLap(store, primarySessionId, primaryLapIndex)
    }
    val reference by produceState<CompareLap?>(null, referenceSessionId, referenceLapIndex) {
        value = loadLap(store, referenceSessionId, referenceLapIndex)
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val primaryPlayer = remember(primary?.clip?.path) {
        primary?.clip?.let { newPlayer(context) }
    }
    val referencePlayer = remember(reference?.clip?.path) {
        reference?.clip?.let { newPlayer(context).apply { volume = 0f } }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var renderedUs by remember { mutableLongStateOf(-1L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var frameStepMs by remember { mutableLongStateOf(33L) }
    var frameDurationUs by remember { mutableLongStateOf(33_000L) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubBaseUs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
    var referenceDurationMs by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }

    fun seekPrimary(targetMs: Long) {
        val player = primaryPlayer ?: return
        val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(clamped)
        positionMs = clamped
        renderedUs = -1L
    }

    val primarySeeker = remember(primaryPlayer) {
        ScrubSeeker(scope, exactWaitMs = SCRUB_EXACT_WAIT_MS) { seekPrimary(it) }
    }
    val referenceSeeker = remember(referencePlayer) {
        ScrubSeeker(scope, exactWaitMs = SCRUB_EXACT_WAIT_MS) { target ->
            referencePlayer?.seekTo(
                target.coerceIn(0L, referenceDurationMs.coerceAtLeast(0L)),
            )
        }
    }

    DisposableEffect(primaryPlayer) {
        val player = primaryPlayer ?: return@DisposableEffect onDispose {}
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
            primarySeeker.onFrameRendered()
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(referencePlayer) {
        val player = referencePlayer ?: return@DisposableEffect onDispose {}
        player.setVideoFrameMetadataListener { _, _, _, _ ->
            referenceSeeker.onFrameRendered()
        }
        onDispose { player.release() }
    }

    val primaryVersion = remember(primary?.clip?.absolutePath) {
        primary?.clip?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
    }
    val referenceVersion = remember(reference?.clip?.absolutePath) {
        reference?.clip?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
    }

    LaunchedEffect(primaryVersion, primaryPlayer) {
        val player = primaryPlayer ?: return@LaunchedEffect
        val loaded = primary ?: return@LaunchedEffect
        val file = loaded.clip ?: return@LaunchedEffect
        renderedUs = -1L
        scrubTargetMs = null
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        // A clip is at least as long as its lap plus the lead-in; keep the controls
        // usable when the platform cannot report a duration.
        durationMs = maxOf(probe.durationMs, loaded.lap.lapTimeMs + loaded.lap.leadInMs.toLong())
        frameStepMs = probe.frameMs
        frameDurationUs = probe.frameDurationUs
        val start = PlaybackCursor.clipMs(0, loaded.lap.leadInMs, loaded.lap.syncOffsetMs)
            .coerceIn(0L, durationMs)
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.seekTo(start)
            positionMs = start
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(referenceVersion, referencePlayer) {
        val player = referencePlayer ?: return@LaunchedEffect
        val loaded = reference ?: return@LaunchedEffect
        val file = loaded.clip ?: return@LaunchedEffect
        val probe = withContext(Dispatchers.IO) { probeClip(file) }
        referenceDurationMs =
            maxOf(probe.durationMs, loaded.lap.lapTimeMs + loaded.lap.leadInMs.toLong())
        val start = PlaybackCursor.clipMs(0, loaded.lap.leadInMs, loaded.lap.syncOffsetMs)
            .coerceIn(0L, referenceDurationMs)
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.seekTo(start)
            player.prepare()
        }
    }

    LaunchedEffect(primaryPlayer) {
        val player = primaryPlayer ?: return@LaunchedEffect
        while (true) {
            if (player.duration > 0) durationMs = maxOf(durationMs, player.duration)
            if (player.isPlaying) positionMs = player.currentPosition
            delay(50)
        }
    }

    LaunchedEffect(primaryPlayer, playbackSpeed) {
        primaryPlayer?.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(primaryPlayer, looping) {
        primaryPlayer?.repeatMode =
            if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(landscape) {
        if (!landscape) primaryPlayer?.pause()
    }

    val primaryLap = primary?.lap
    // While the ruler is under a finger, show the requested frame rather than the
    // last one the decoder managed to render.
    val displayedUs = scrubTargetMs?.times(1_000)
        ?: if (renderedUs >= 0) renderedUs else positionMs * 1_000
    val displayedMs = scrubTargetMs
        ?: if (renderedUs >= 0) renderedUs / 1_000 else positionMs
    val primaryCursorMs = primaryLap?.let {
        PlaybackCursor.telemetryMs(displayedMs, it.leadInMs, it.syncOffsetMs, it.lapTimeMs)
    } ?: 0

    val primaryWindow = remember(primary) {
        primary?.let { it.samples.lapWindow(it.lap) } ?: emptyList()
    }
    val referenceWindow = remember(reference) {
        reference?.let { it.samples.lapWindow(it.lap) } ?: emptyList()
    }
    val primaryTable = remember(primaryWindow, primary) {
        primary?.let { TrackProgress.table(primaryWindow, it.lap.startNanos) }
    }
    val referenceTable = remember(referenceWindow, reference) {
        reference?.let { TrackProgress.table(referenceWindow, it.lap.startNanos) }
    }
    val progress = primaryTable?.percentAt(primaryCursorMs) ?: 0f
    val referenceCursorMs = referenceCursor(
        primaryTable = primaryTable,
        primaryCursorMs = primaryCursorMs,
        referenceTable = referenceTable,
        referenceLapTimeMs = reference?.lap?.lapTimeMs ?: 0,
    )
    val referenceClipMs = reference?.let { loaded ->
        PlaybackCursor.clipMs(referenceCursorMs, loaded.lap.leadInMs, loaded.lap.syncOffsetMs)
            .coerceIn(0L, referenceDurationMs.coerceAtLeast(0L))
    } ?: 0L

    // One seek per rendered primary frame, coalesced so a scrub cannot queue a storm.
    LaunchedEffect(referenceSeeker, referenceClipMs) {
        if (referencePlayer == null || primaryPlayer == null) return@LaunchedEffect
        referenceSeeker.submit(referenceClipMs)
    }

    val events = remember(primaryWindow, primaryLap) {
        primaryLap?.let { lapEvents(primaryWindow, it) } ?: emptyList()
    }
    val brakeTimes = remember(events) {
        events.filter { it.type == LapEventType.Brake }.map { it.timeMs }
    }
    val primaryStats = remember(primaryWindow, primaryLap) {
        primaryLap?.let { lapStats(primaryWindow, it) }
    }
    val referenceStats = remember(referenceWindow, reference) {
        reference?.let { lapStats(referenceWindow, it.lap) }
    }

    fun seekLap(targetMs: Long) {
        val lap = primaryLap ?: return
        val clamped = targetMs.coerceIn(0L, lap.lapTimeMs.toLong())
        seekPrimary(PlaybackCursor.clipMs(clamped.toInt(), lap.leadInMs, lap.syncOffsetMs))
        primaryPlayer?.pause()
        scrubTargetMs = null
        primarySeeker.clearPending()
    }

    fun scrubTarget(baseUs: Long, frames: Int): Long? {
        val lap = primaryLap ?: return null
        val startMs = PlaybackCursor.clipMs(0, lap.leadInMs, lap.syncOffsetMs).coerceAtLeast(0)
        val endMs = PlaybackCursor.clipMs(lap.lapTimeMs, lap.leadInMs, lap.syncOffsetMs)
            .coerceAtMost(durationMs.coerceAtLeast(0))
        return frameStepTargetMs(baseUs, frames, frameDurationUs)
            .coerceIn(startMs, endMs.coerceAtLeast(startMs))
    }

    fun stepFrames(frames: Int) {
        val target = frameStepTargetMs(displayedUs, frames, frameDurationUs)
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
        scrubTargetMs = target
        primarySeeker.submit(target)
    }

    fun seekBrake(direction: Int) {
        val target = if (direction > 0) {
            brakeTimes.firstOrNull { it > primaryCursorMs + 200 }
        } else {
            brakeTimes.lastOrNull { it < primaryCursorMs - 200 }
        }
        if (target != null) seekLap(target.toLong())
    }

    LaunchedEffect(scrubbing, renderedUs) {
        val target = scrubTargetMs ?: return@LaunchedEffect
        if (scrubbing) return@LaunchedEffect
        if (renderedUs >= 0 && abs(renderedUs / 1_000 - target) <= frameStepMs * 2) {
            scrubTargetMs = null
        }
    }

    LaunchedEffect(scrubbing) {
        if (scrubbing) return@LaunchedEffect
        val target = scrubTargetMs ?: return@LaunchedEffect
        delay(1500)
        if (scrubTargetMs == target) scrubTargetMs = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                title = "Compare",
                subtitle = primary?.let {
                    "${it.trackLabel} · ${lapTimeLabel(it.lap.lapTimeMs)} vs " +
                        lapTimeLabel(reference?.lap?.lapTimeMs ?: 0)
                },
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparePane(
                        lap = primary,
                        player = primaryPlayer,
                        title = "Primary",
                        cursorMs = primaryCursorMs,
                        onAddClip = null,
                        modifier = Modifier.weight(1f),
                    )
                    ComparePane(
                        lap = reference,
                        player = referencePlayer,
                        title = "Reference",
                        cursorMs = referenceCursorMs,
                        onAddClip = { onAddClip(referenceSessionId, referenceLapIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }

                message?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (primaryTable != null && referenceTable != null) {
                    val deltaMs = primaryCursorMs - referenceCursorMs
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                deltaLabel(deltaMs),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (deltaMs <= 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            NumericText(
                                "%.1f%% · ref %s".format(progress, formatMillis(referenceCursorMs.toLong())),
                                modifier = Modifier.testTag("compare-reference-time"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        primaryStats?.let {
                            Text(
                                lapStatsComparison(
                                    it,
                                    referenceStats,
                                    settings.imperialUnits,
                                    label = "reference",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Track map",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TrackMapLegend(showReference = true)
                    }
                    TrackMap(
                        samples = primary?.samples ?: emptyList(),
                        lap = primaryLap ?: return@SectionCard,
                        cursorMs = primaryCursorMs,
                        follow = true,
                        reference = reference?.let { GhostLap(it.lap, it.samples) },
                        referenceCursorMs = referenceCursorMs,
                    )
                }

                SectionCard {
                    ChartColumn(
                        samples = primary?.samples ?: emptyList(),
                        lap = primaryLap ?: return@SectionCard,
                        cursorMs = primaryCursorMs,
                        imperial = settings.imperialUnits,
                        reference = reference?.let { GhostLap(it.lap, it.samples) },
                    )
                }
            }

            if (primaryPlayer != null) {
                PlaybackFooter(
                    cursorMs = primaryCursorMs,
                    lapTimeMs = primaryLap?.lapTimeMs ?: 0,
                    frameStepMs = frameStepMs,
                    enabled = durationMs > 0,
                    playing = playing,
                    looping = looping,
                    speed = playbackSpeed,
                    events = events,
                    hapticsEnabled = settings.hapticsEnabled,
                    onScrubStart = {
                        scrubBaseUs = displayedUs
                        scrubTargetMs = null
                        scrubbing = true
                        primaryPlayer.pause()
                    },
                    onScrub = { frames ->
                        scrubTarget(scrubBaseUs, frames)?.let { target ->
                            scrubTargetMs = target
                            primarySeeker.submit(target)
                        }
                    },
                    onScrubEnd = {
                        scrubbing = false
                        scrubTargetMs?.let { target ->
                            seekPrimary(target)
                            primaryPlayer.pause()
                        }
                    },
                    onSeekFraction = { fraction ->
                        seekLap((fraction * (primaryLap?.lapTimeMs ?: 0)).roundToLong())
                    },
                    onRewind = { seekBrake(-1) },
                    onPlayPause = {
                        if (primaryPlayer.isPlaying) {
                            primaryPlayer.pause()
                        } else {
                            if (primaryCursorMs >= (primaryLap?.lapTimeMs ?: 0)) seekLap(0)
                            primaryPlayer.play()
                        }
                    },
                    onForward = { seekBrake(1) },
                    onToggleLoop = { looping = !looping },
                    onSpeedChange = { playbackSpeed = it },
                    onStep = { stepFrames(it) },
                )
            }
        }

        if (!landscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("compare-rotate"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Rotate to landscape", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Comparing laps needs the wider view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparePane(
    lap: CompareLap?,
    player: ExoPlayer?,
    title: String,
    cursorMs: Int,
    onAddClip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lap?.let { "$title · Lap ${it.lap.index}" } ?: title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumericText(
                formatMillis(cursorMs.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                player != null && lap?.clip != null -> AndroidView(
                    factory = { viewContext ->
                        (LayoutInflater.from(viewContext)
                            .inflate(R.layout.view_lap_player, null) as PlayerView).apply {
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize().testTag("compare-video"),
                )
                lap != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (lap.samples.isEmpty()) "No track data" else "No clip yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    onAddClip?.let {
                        PrimaryPill(
                            text = "Add clip",
                            onClick = it,
                            modifier = Modifier.testTag("compare-add-clip"),
                        )
                    }
                }
                else -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Lap time on the reference lap at the primary lap's track progress. */
internal fun referenceCursor(
    primaryTable: TrackProgress.Table?,
    primaryCursorMs: Int,
    referenceTable: TrackProgress.Table?,
    referenceLapTimeMs: Int,
): Int = referenceTable?.timeAtPercent(
    primaryTable?.percentAt(primaryCursorMs) ?: 0f,
    referenceLapTimeMs,
) ?: 0

private fun deltaLabel(deltaMs: Int): String = when {
    deltaMs == 0 -> "Δ 0.000 s"
    deltaMs < 0 -> "Δ −%.3f s ahead".format(-deltaMs / 1000.0)
    else -> "Δ +%.3f s behind".format(deltaMs / 1000.0)
}

@OptIn(UnstableApi::class)
private fun newPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setSeekParameters(SeekParameters.EXACT)
    }

private suspend fun loadLap(
    store: SessionStore,
    sessionId: String,
    lapIndex: Int,
): CompareLap? = withContext(Dispatchers.IO) {
    val session = store.load(sessionId) ?: return@withContext null
    val lap = session.laps.firstOrNull { it.index == lapIndex } ?: return@withContext null
    val samples = runCatching { store.readSamples(sessionId, lapIndex) }
        .getOrDefault(emptyList())
    val clip = lap.videoFile
        .takeIf { it.isNotBlank() && !lap.needsCut && it != session.rawFile }
        ?.let { File(store.sessionDir(sessionId), it) }
        ?.takeIf { it.isFile }
    CompareLap(sessionId, lapIndex, session.trackLabel, lap, samples, clip)
}
