package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal const val TRANSPORT_MS = 10_000L

/** Playback rates offered in Settings and the lap footer. */
internal val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

internal fun playbackSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) "%.0f×".format(speed) else "%.1f×".format(speed)

/** The next rate in [PLAYBACK_SPEEDS]; clamps at the ends. */
internal fun steppedPlaybackSpeed(speed: Float, direction: Int): Float {
    val current = PLAYBACK_SPEEDS.indexOf(speed).takeIf { it >= 0 }
        ?: PLAYBACK_SPEEDS.indexOf(1f)
    return PLAYBACK_SPEEDS[(current + direction).coerceIn(0, PLAYBACK_SPEEDS.lastIndex)]
}

/** The next rate in [PLAYBACK_SPEEDS], wrapping at the end. */
internal fun nextPlaybackSpeed(speed: Float): Float {
    val current = PLAYBACK_SPEEDS.indexOf(speed).takeIf { it >= 0 }
        ?: PLAYBACK_SPEEDS.indexOf(1f)
    return PLAYBACK_SPEEDS[(current + 1) % PLAYBACK_SPEEDS.size]
}

private val FrameStepWidth = 6.dp

/** Turns horizontal drag pixels into whole frames, carrying the remainder and the drag total. */
internal class FrameSteps(private var pxPerFrame: Float) {
    private var pendingPx = 0f

    var totalFrames = 0
        private set

    fun framesFor(dxPx: Float): Int {
        pendingPx += dxPx
        val frames = (pendingPx / pxPerFrame).toInt()
        pendingPx -= frames * pxPerFrame
        totalFrames += frames
        return frames
    }

    fun updatePxPerFrame(value: Float) {
        pxPerFrame = value
        pendingPx = 0f
    }

    fun reset(startFrames: Int = 0) {
        pendingPx = 0f
        totalFrames = startFrames
    }
}

@Composable
internal fun TransportRow(
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
    size: Dp = if (primary) 64.dp else 52.dp,
    iconSize: Dp = if (primary) 34.dp else 26.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
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
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Sticky playback controls for the lap screen. */
@Composable
internal fun PlaybackFooter(
    cursorMs: Int,
    lapTimeMs: Int,
    frameStepMs: Long,
    enabled: Boolean,
    playing: Boolean,
    looping: Boolean,
    speed: Float,
    events: List<LapEvent>,
    hapticsEnabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onToggleLoop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStep: (Int) -> Unit = {},
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(thickness = 1.dp, color = Hairline)
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransportControls(
                    playing = playing,
                    enabled = enabled,
                    hapticsEnabled = hapticsEnabled,
                    onRewind = onRewind,
                    onPlayPause = onPlayPause,
                    onForward = onForward,
                    onStep = onStep,
                )
                FrameRuler(
                    valueMs = cursorMs.toLong(),
                    startMs = 0,
                    endMs = lapTimeMs.toLong(),
                    frameStepMs = frameStepMs,
                    enabled = enabled,
                    onScrubStart = onScrubStart,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    events = events,
                    showProgress = true,
                    onSeekFraction = onSeekFraction,
                    hapticsEnabled = hapticsEnabled,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SpeedCycle(speed = speed, enabled = enabled, onSpeedChange = onSpeedChange)
                LoopButton(looping = looping, enabled = enabled, onToggle = onToggleLoop)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumericText(
                        "%s / %s".format(formatMillis(cursorMs.toLong()), lapTimeLabel(lapTimeMs)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FilterChip(
                        selected = looping,
                        onClick = onToggleLoop,
                        label = { Text("Loop") },
                    )
                    SpeedStepper(speed = speed, onSpeedChange = onSpeedChange)
                }
                FrameRuler(
                    valueMs = cursorMs.toLong(),
                    startMs = 0,
                    endMs = lapTimeMs.toLong(),
                    frameStepMs = frameStepMs,
                    enabled = enabled,
                    onScrubStart = onScrubStart,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    events = events,
                    showProgress = true,
                    onSeekFraction = onSeekFraction,
                    hapticsEnabled = hapticsEnabled,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    TransportControls(
                        modifier = Modifier.align(Alignment.Center),
                        playing = playing,
                        enabled = enabled,
                        hapticsEnabled = hapticsEnabled,
                        onRewind = onRewind,
                        onPlayPause = onPlayPause,
                        onForward = onForward,
                        onStep = onStep,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportControls(
    playing: Boolean,
    enabled: Boolean,
    hapticsEnabled: Boolean,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransportButton(
            icon = TransportIcons.SkipPrevious,
            description = "Previous brake point",
            enabled = enabled,
            onClick = onRewind,
            size = 36.dp,
            iconSize = 20.dp,
        )
        StepButton(
            text = "−1",
            onClick = { onStep(-1) },
            enabled = enabled,
            hapticsEnabled = hapticsEnabled,
            compact = true,
            description = "Step back one frame",
            modifier = Modifier.testTag("frame-back"),
        )
        TransportButton(
            icon = if (playing) TransportIcons.Pause else Icons.Filled.PlayArrow,
            description = if (playing) "Pause" else "Play",
            enabled = enabled,
            onClick = onPlayPause,
            primary = true,
            size = 44.dp,
            iconSize = 24.dp,
        )
        StepButton(
            text = "+1",
            onClick = { onStep(1) },
            enabled = enabled,
            hapticsEnabled = hapticsEnabled,
            compact = true,
            description = "Step forward one frame",
            modifier = Modifier.testTag("frame-forward"),
        )
        TransportButton(
            icon = TransportIcons.SkipNext,
            description = "Next brake point",
            enabled = enabled,
            onClick = onForward,
            size = 36.dp,
            iconSize = 20.dp,
        )
    }
}

@Composable
private fun SpeedCycle(speed: Float, enabled: Boolean, onSpeedChange: (Float) -> Unit) {
    TextButton(
        onClick = { onSpeedChange(nextPlaybackSpeed(speed)) },
        enabled = enabled,
        modifier = Modifier.size(38.dp).testTag("speed-cycle"),
        contentPadding = PaddingValues(0.dp),
    ) {
        NumericText(playbackSpeedLabel(speed), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LoopButton(looping: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (looping) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                } else {
                    Modifier
                },
            )
            .testTag("loop-toggle"),
    ) {
        Icon(
            imageVector = TransportIcons.Loop,
            contentDescription = if (looping) "Loop on" else "Loop lap",
            tint = if (looping) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SpeedStepper(speed: Float, onSpeedChange: (Float) -> Unit) {
    val index = PLAYBACK_SPEEDS.indexOf(speed).takeIf { it >= 0 }
        ?: PLAYBACK_SPEEDS.indexOf(1f)
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onSpeedChange(PLAYBACK_SPEEDS[index - 1]) },
            enabled = index > 0,
            modifier = Modifier.size(40.dp).testTag("speed-down"),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("−", style = MaterialTheme.typography.labelLarge)
        }
        Box(
            modifier = Modifier.widthIn(min = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            NumericText(
                text = playbackSpeedLabel(speed),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        TextButton(
            onClick = { onSpeedChange(PLAYBACK_SPEEDS[index + 1]) },
            enabled = index < PLAYBACK_SPEEDS.lastIndex,
            modifier = Modifier.size(40.dp).testTag("speed-up"),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("+", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Fine steps need tighter padding than a full-width pill to keep the labels on one line. */
@Composable
internal fun StepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    compact: Boolean = false,
    description: String = text,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {},
        enabled = enabled,
        modifier = modifier
            .then(if (compact) Modifier.size(36.dp) else Modifier.height(42.dp))
            .semantics {
                contentDescription = description
                onClick(label = description) {
                    onClick()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onClick()
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    val repeat = scope.launch {
                        delay(400)
                        while (isActive) {
                            onClick()
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            delay(80)
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        repeat.cancel()
                    }
                }
            },
        shape = CircleShape,
        contentPadding = if (compact) PaddingValues(0.dp) else PaddingValues(horizontal = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
internal fun FrameStepRow(
    enabled: Boolean,
    onStep: (Int) -> Unit,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepButton("−1 frame", { onStep(-1) }, Modifier.weight(1f), enabled, hapticsEnabled)
        StepButton("+1 frame", { onStep(1) }, Modifier.weight(1f), enabled, hapticsEnabled)
    }
}

/**
 * A fixed needle over a horizontally scrolling ruler. Each 6 dp of travel is one
 * video frame; the slider above handles distance. Pressing jumps to the timestamp
 * under the finger, then dragging steps frames from there. The strip follows the
 * finger, so dragging right rewinds and dragging left moves forward. The ruler
 * reports how far the drag has travelled in total, so callers can seek from the
 * playhead they held when the drag started, and reports the end of the drag so
 * callers can settle the playhead.
 */
@Composable
internal fun FrameRuler(
    valueMs: Long,
    startMs: Long,
    endMs: Long,
    frameStepMs: Long,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (framesFromStart: Int) -> Unit,
    onScrubEnd: () -> Unit = {},
    events: List<LapEvent> = emptyList(),
    showProgress: Boolean = false,
    onSeekFraction: (Float) -> Unit = {},
    hapticsEnabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minPxPerFrame = with(density) { 3.dp.toPx() }
    val maxPxPerFrame = with(density) { 14.dp.toPx() }
    val pxPerFrameState = remember {
        mutableFloatStateOf(with(density) { FrameStepWidth.toPx() })
    }
    val pxPerFrame = pxPerFrameState.floatValue
    val accumulator = remember { FrameSteps(pxPerFrame) }
    val haptics = LocalHapticFeedback.current
    // The pointer coroutine outlives recomposition, so never capture the callbacks directly.
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentOnSeekFraction by rememberUpdatedState(onSeekFraction)
    val currentHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val textMeasurer = rememberTextMeasurer()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val eventColors = mapOf(
        LapEventType.Brake to MaterialTheme.colorScheme.error,
        LapEventType.Throttle to MaterialTheme.colorScheme.primary,
        LapEventType.Apex to MaterialTheme.colorScheme.tertiary,
        LapEventType.Exit to MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = GeistMonoFamily,
        color = labelColor,
        letterSpacing = 0.sp,
        fontSize = if (compact) 9.sp else MaterialTheme.typography.labelSmall.fontSize,
    )
    val step = frameStepMs.coerceAtLeast(1L)
    val labelEvery = (500L / step).coerceAtLeast(1L)
    val progress = ((valueMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 40.dp else 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .alpha(if (enabled) 1f else 0.5f)
            .semantics {
                contentDescription = "Frame ruler"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        currentOnSeekFraction(value)
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Press selects the timestamp under the finger; the drag then steps frames from it.
                    val pressedFrames =
                        ((down.position.x - size.width / 2f) / pxPerFrameState.floatValue)
                            .roundToInt()
                    accumulator.reset(pressedFrames)
                    currentOnScrubStart()
                    if (pressedFrames != 0) {
                        if (currentHapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        currentOnScrub(accumulator.totalFrames)
                    }
                    var zoomed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f) {
                            zoomed = true
                            val next = (pxPerFrameState.floatValue * zoom)
                                .coerceIn(minPxPerFrame, maxPxPerFrame)
                            pxPerFrameState.floatValue = next
                            accumulator.updatePxPerFrame(next)
                            event.changes.forEach { it.consume() }
                        } else if (!zoomed && pan.x != 0f) {
                            event.changes.forEach { it.consume() }
                            // The needle is fixed, so the ruler is a physical strip:
                            // dragging right pulls earlier frames under the needle.
                            val frames = accumulator.framesFor(-pan.x)
                            if (frames != 0) {
                                if (currentHapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                currentOnScrub(accumulator.totalFrames)
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    accumulator.reset()
                    currentOnScrubEnd()
                }
            },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val centerX = size.width / 2f
        val baseline = size.height - (if (compact) 20.dp else 24.dp).toPx()
        val needleTop = (if (compact) 4.dp else 6.dp).toPx()
        val pxPerMs = pxPerFrame / step.toFloat()
        val halfSpan = centerX / pxPerMs
        val firstFrame = floor((valueMs - halfSpan) / step).toLong()
        val lastFrame = ceil((valueMs + halfSpan) / step).toLong()

        for (frame in firstFrame..lastFrame) {
            val time = frame * step
            if (time < startMs || time > endMs) continue
            val x = centerX + (time - valueMs) * pxPerMs
            if (x < 0f || x > size.width) continue
            val major = frame % labelEvery == 0L
            val tick = when {
                major -> (if (compact) 18.dp else 26.dp).toPx()
                frame % 5 == 0L -> (if (compact) 13.dp else 18.dp).toPx()
                else -> (if (compact) 9.dp else 12.dp).toPx()
            }
            drawLine(
                color = tickColor,
                start = Offset(x, baseline - tick),
                end = Offset(x, baseline),
                strokeWidth = 1.dp.toPx(),
            )
            if (major) {
                val layout = textMeasurer.measure(formatRulerLabel(time), labelStyle)
                val pad = 4.dp.toPx()
                val maxX = (size.width - layout.size.width - pad).coerceAtLeast(pad)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        (x - layout.size.width / 2f).coerceIn(pad, maxX),
                        baseline + 4.dp.toPx(),
                    ),
                )
            }
        }

        drawLine(
            color = tickColor.copy(alpha = 0.5f),
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = needleColor,
            start = Offset(centerX, needleTop),
            end = Offset(centerX, baseline),
            strokeWidth = 2.dp.toPx(),
        )
        drawPath(
            path = Path().apply {
                moveTo(centerX - 5.dp.toPx(), needleTop)
                lineTo(centerX + 5.dp.toPx(), needleTop)
                lineTo(centerX, needleTop + 6.dp.toPx())
                close()
            },
            color = needleColor,
        )
        events.forEach { event ->
            if (event.timeMs < startMs || event.timeMs > endMs) return@forEach
            val x = centerX + (event.timeMs.toLong() - valueMs).toFloat() * pxPerMs
            if (x < 0f || x > size.width) return@forEach
            drawCircle(
                color = eventColors.getValue(event.type),
                radius = 2.dp.toPx(),
                center = Offset(x, 4.dp.toPx()),
            )
        }
        if (showProgress) {
            val span = (endMs - startMs).coerceAtLeast(1L)
            val fraction = ((valueMs - startMs).toFloat() / span).coerceIn(0f, 1f)
            val progressHeight = if (compact) 3.dp else 4.dp
            val trackTop = size.height - progressHeight.toPx()
            val trackSize = Size(size.width, progressHeight.toPx())
            val corner = CornerRadius(progressHeight.toPx() / 2f)
            drawRoundRect(
                color = tickColor.copy(alpha = 0.25f),
                topLeft = Offset(0f, trackTop),
                size = trackSize,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = needleColor.copy(alpha = 0.75f),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width * fraction, progressHeight.toPx()),
                cornerRadius = corner,
            )
        }
    }
}

internal fun formatRulerLabel(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) + 50) / 100
    val minutes = tenths / 600
    val seconds = (tenths % 600) / 10
    return "%d:%02d.%d".format(minutes, seconds, tenths % 10)
}

/**
 * Seeks into the target frame, not before it: a frame's presentation time in
 * microseconds is rounded up to the next millisecond.
 */
internal fun frameStepTargetMs(positionUs: Long, frames: Int, frameDurationUs: Long): Long {
    val targetUs = positionUs + frames * frameDurationUs
    return if (targetUs <= 0) 0 else (targetUs + 999) / 1_000
}
