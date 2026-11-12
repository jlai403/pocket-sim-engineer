package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Turns rotary drags into whole frames, carrying the fraction so slow turns still
 * advance one frame at a time.
 */
internal class JogAccumulator(private val framesPerTurn: Int) {
    private var pending = 0f

    fun framesFor(turns: Float): Int {
        pending += turns * framesPerTurn
        val whole = pending.toInt()
        pending -= whole
        return whole
    }
}

private const val TWO_PI = 2f * PI.toFloat()

/** Shortest signed angle between two directions, in radians. */
internal fun wrapRadians(radians: Float): Float {
    var value = radians % TWO_PI
    if (value > PI.toFloat()) value -= TWO_PI
    if (value < -PI.toFloat()) value += TWO_PI
    return value
}

/**
 * iPod-style rotary scrubbing: one full turn moves one second of frames, so the
 * timeline slider handles distance and this handles precision.
 */
@Composable
internal fun JogWheel(
    frameStepMs: Long,
    enabled: Boolean,
    onFrames: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val framesPerTurn = (1_000L / frameStepMs.coerceAtLeast(1L)).toInt().coerceAtLeast(1)
    val accumulator = remember(framesPerTurn) { JogAccumulator(framesPerTurn) }
    val haptics = LocalHapticFeedback.current
    val ringColor = MaterialTheme.colorScheme.surfaceVariant
    val knobColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    var knobAngle by remember { mutableFloatStateOf(-PI.toFloat() / 2f) }
    var lastAngle by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = 0.35f))
            .semantics { contentDescription = "Frame jog wheel" }
            .pointerInput(enabled, framesPerTurn) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { lastAngle = angleAt(it, size) },
                    onDrag = { change, _ ->
                        change.consume()
                        val angle = angleAt(change.position, size)
                        val previous = lastAngle
                        lastAngle = angle
                        if (previous != null && !nearCentre(change.position, size)) {
                            val turn = wrapRadians(angle - previous) / TWO_PI
                            knobAngle = wrapRadians(knobAngle + turn * TWO_PI)
                            val frames = accumulator.framesFor(turn)
                            if (frames != 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onFrames(frames)
                            }
                        }
                    },
                    onDragEnd = { lastAngle = null },
                    onDragCancel = { lastAngle = null },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            drawCircle(color = ringColor, radius = radius, style = Stroke(stroke))
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = knobColor,
                radius = 8.dp.toPx(),
                center = Offset(
                    centre.x + cos(knobAngle) * radius,
                    centre.y + sin(knobAngle) * radius,
                ),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("1 turn", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("= 1 s", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

private fun angleAt(position: Offset, size: IntSize): Float =
    atan2(position.y - size.height / 2f, position.x - size.width / 2f)

/** Skipping the centre avoids a 180° jump when the finger crosses it. */
private fun nearCentre(position: Offset, size: IntSize): Boolean =
    hypot(position.x - size.width / 2f, position.y - size.height / 2f) <
        minOf(size.width, size.height) * 0.18f
