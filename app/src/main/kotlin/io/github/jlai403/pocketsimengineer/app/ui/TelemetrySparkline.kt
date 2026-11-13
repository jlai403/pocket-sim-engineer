package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.ceil

/** A tiny unlabeled trace for the readout row and video HUD. */
@Composable
internal fun TelemetrySparkline(
    window: List<Sample>,
    lap: LapMetadata,
    valueOf: (Sample) -> Float,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (window.isEmpty()) return
    val startMs = window.minOf { it.lapTimeMs(lap) }
    val endMs = window.maxOf { it.lapTimeMs(lap) }
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        if (window.size == 1) {
            val sample = window.first()
            val radius = 2.dp.toPx()
            val y = size.height -
                (valueOf(sample) / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f) * size.height
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(size.width - radius, y),
            )
            return@Canvas
        }
        val step = ceil(window.size / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val path = Path()
        var first = true
        for (index in window.indices step step) {
            val sample = window[index]
            val x = windowTimeToX(sample.lapTimeMs(lap), startMs, endMs, size.width)
            val y = size.height -
                (valueOf(sample) / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f) * size.height
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f))
    }
}

/** Maps a time inside [startMs]..[endMs] across the widget, not across the whole lap. */
internal fun windowTimeToX(timeMs: Int, startMs: Int, endMs: Int, width: Float): Float =
    ((timeMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f) * width
