package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

internal const val TRANSPORT_MS = 10_000L

/** Playback rates offered in Settings and the lap footer. */
internal val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

internal fun playbackSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) "%.0f×".format(speed) else "%.1f×".format(speed)

private val FrameStepWidth = 6.dp

/** Turns horizontal drag pixels into whole frames, carrying the remainder and the drag total. */
internal class FrameSteps(private val pxPerFrame: Float) {
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

    fun reset() {
        pendingPx = 0f
        totalFrames = 0
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
    speed: Float,
    events: List<LapEvent>,
    hapticsEnabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(thickness = 1.dp, color = Hairline)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NumericText(
                "%s / %s".format(formatMillis(cursorMs.toLong()), lapTimeLabel(lapTimeMs)),
                style = MaterialTheme.typography.labelMedium,
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
                hapticsEnabled = hapticsEnabled,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TransportButton(
                    icon = TransportIcons.Rewind,
                    description = "Rewind 10 seconds",
                    enabled = enabled,
                    onClick = onRewind,
                    size = 36.dp,
                    iconSize = 20.dp,
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
                TransportButton(
                    icon = TransportIcons.Forward,
                    description = "Forward 10 seconds",
                    enabled = enabled,
                    onClick = onForward,
                    size = 36.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.weight(1f))
                SpeedPill(speed = speed, onSpeedChange = onSpeedChange)
            }
        }
    }
}

@Composable
private fun SpeedPill(speed: Float, onSpeedChange: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GhostPill(
            text = playbackSpeedLabel(speed),
            onClick = { open = true },
            modifier = Modifier.testTag("speed-menu"),
            trailingIcon = Icons.Filled.KeyboardArrowDown,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PLAYBACK_SPEEDS.forEach { rate ->
                DropdownMenuItem(
                    text = { Text(playbackSpeedLabel(rate)) },
                    onClick = {
                        onSpeedChange(rate)
                        open = false
                    },
                )
            }
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
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
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
internal fun FrameStepRow(
    enabled: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StepButton("−1 frame", { onStep(-1) }, Modifier.weight(1f), enabled)
        StepButton("+1 frame", { onStep(1) }, Modifier.weight(1f), enabled)
    }
}

/**
 * A fixed needle over a horizontally scrolling ruler. Each 6 dp of travel is one
 * video frame; the slider above handles distance. The strip follows the finger,
 * so dragging right rewinds and dragging left moves forward. The ruler reports
 * how far the drag has travelled in total, so callers can seek from the playhead
 * they held when the drag started, and reports the end of the drag so callers
 * can settle the playhead.
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
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerFrame = with(density) { FrameStepWidth.toPx() }
    val accumulator = remember(pxPerFrame) { FrameSteps(pxPerFrame) }
    val haptics = LocalHapticFeedback.current
    // The pointer coroutine outlives recomposition, so never capture the callbacks directly.
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val currentHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val textMeasurer = rememberTextMeasurer()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val eventColors = mapOf(
        LapEventType.Brake to MaterialTheme.colorScheme.error,
        LapEventType.Coast to MaterialTheme.colorScheme.onSurfaceVariant,
        LapEventType.Throttle to MaterialTheme.colorScheme.primary,
        LapEventType.Turn to MaterialTheme.colorScheme.tertiary,
    )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = GeistMonoFamily,
        color = labelColor,
    )
    val step = frameStepMs.coerceAtLeast(1L)
    val labelEvery = (500L / step).coerceAtLeast(1L)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .alpha(if (enabled) 1f else 0.5f)
            .semantics { contentDescription = "Frame ruler" }
            .pointerInput(enabled, pxPerFrame, step) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulator.reset()
                        currentOnScrubStart()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // The needle is fixed, so the ruler is a physical strip: dragging
                        // right pulls earlier frames under the needle.
                        val frames = accumulator.framesFor(-dragAmount)
                        if (frames != 0) {
                            if (currentHapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentOnScrub(accumulator.totalFrames)
                        }
                    },
                    onDragCancel = {
                        accumulator.reset()
                        currentOnScrubEnd()
                    },
                    onDragEnd = {
                        accumulator.reset()
                        currentOnScrubEnd()
                    },
                )
            },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val centerX = size.width / 2f
        val baseline = size.height - 24.dp.toPx()
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
                major -> 26.dp.toPx()
                frame % 5 == 0L -> 18.dp.toPx()
                else -> 12.dp.toPx()
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
            start = Offset(centerX, 6.dp.toPx()),
            end = Offset(centerX, baseline),
            strokeWidth = 2.dp.toPx(),
        )
        drawPath(
            path = Path().apply {
                moveTo(centerX - 5.dp.toPx(), 6.dp.toPx())
                lineTo(centerX + 5.dp.toPx(), 6.dp.toPx())
                lineTo(centerX, 12.dp.toPx())
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
            val trackTop = size.height - 4.dp.toPx()
            val trackSize = Size(size.width, 4.dp.toPx())
            val corner = CornerRadius(2.dp.toPx())
            drawRoundRect(
                color = tickColor.copy(alpha = 0.25f),
                topLeft = Offset(0f, trackTop),
                size = trackSize,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = needleColor.copy(alpha = 0.75f),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width * fraction, 4.dp.toPx()),
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
