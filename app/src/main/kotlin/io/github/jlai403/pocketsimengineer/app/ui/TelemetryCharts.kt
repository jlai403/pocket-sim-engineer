package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.ceil

@Composable
fun ChartColumn(
    samples: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    imperial: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val speedFactor = if (imperial) 2.23694f else 3.6f
    val speedUnit = if (imperial) "mph" else "km/h"
    val window = remember(samples, lap) { samples.lapWindow(lap) }
    if (window.isEmpty()) {
        Text("No telemetry for this lap", modifier = modifier)
        return
    }
    val current = remember(window, lap, cursorMs) { window.nearest(lap, cursorMs) }
    val speedColor = MaterialTheme.colorScheme.secondary
    val throttleColor = MaterialTheme.colorScheme.primary
    val brakeColor = MaterialTheme.colorScheme.error
    val rpmColor = MaterialTheme.colorScheme.tertiary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        current?.let { sample ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NumericText(
                    "%.0f %s".format(sample.speedMps * speedFactor, speedUnit),
                    style = MaterialTheme.typography.labelLarge,
                )
                NumericText("Gear %d".format(sample.gear), style = MaterialTheme.typography.labelLarge)
                NumericText(
                    "Thr %d%%".format(sample.throttle * 100 / 255),
                    style = MaterialTheme.typography.labelLarge,
                )
                NumericText(
                    "Brk %d%%".format(sample.brake * 100 / 255),
                    style = MaterialTheme.typography.labelLarge,
                )
                NumericText(
                    "%.0f rpm".format(sample.rpm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        TraceChart(
            window = window,
            lap = lap,
            cursorMs = cursorMs,
            maxValue = window.maxOf { it.speedMps * speedFactor }.coerceAtLeast(1f),
            valueOf = { it.speedMps * speedFactor },
            color = speedColor,
            label = "Speed",
        )
        TraceChart(
            window = window,
            lap = lap,
            cursorMs = cursorMs,
            maxValue = 255f,
            valueOf = { it.throttle.toFloat() },
            color = throttleColor,
            label = "Throttle",
        )
        TraceChart(
            window = window,
            lap = lap,
            cursorMs = cursorMs,
            maxValue = 255f,
            valueOf = { it.brake.toFloat() },
            color = brakeColor,
            label = "Brake",
        )
        TraceChart(
            window = window,
            lap = lap,
            cursorMs = cursorMs,
            maxValue = window.maxOf { it.rpm }.coerceAtLeast(1f),
            valueOf = { it.rpm },
            color = rpmColor,
            label = "RPM",
        )
        GearTrace(window = window, lap = lap, cursorMs = cursorMs)
    }
}

@Composable
private fun TraceChart(
    window: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    maxValue: Float,
    valueOf: (Sample) -> Float,
    color: Color,
    label: String,
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(vertical = 2.dp),
        ) {
            drawLine(
                color = axisColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f,
            )
            val path = Path()
            val step = ceil(window.size / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            var first = true
            for (index in window.indices step step) {
                val sample = window[index]
                val x = timeToX(sample.lapTimeMs(lap), lap.lapTimeMs, size.width)
                val y = size.height - (valueOf(sample) / maxValue).coerceIn(0f, 1f) * size.height
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(path, color = color, style = Stroke(width = 2f))
            drawCursor(cursorMs, lap.lapTimeMs, size, cursorColor)
        }
    }
}

@Composable
private fun GearTrace(window: List<Sample>, lap: LapMetadata, cursorMs: Int) {
    val cursorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val gearColor = MaterialTheme.colorScheme.tertiary
    Column {
        Text("Gear", style = MaterialTheme.typography.labelSmall)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(vertical = 2.dp),
        ) {
            val gears = window.map { it.gear }
            val minGear = gears.minOrNull() ?: 0
            val maxGear = gears.maxOrNull() ?: 1
            val span = (maxGear - minGear).coerceAtLeast(1)
            val path = Path()
            var previousY = 0f
            var first = true
            val step = ceil(window.size / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            for (index in window.indices step step) {
                val sample = window[index]
                val x = timeToX(sample.lapTimeMs(lap), lap.lapTimeMs, size.width)
                val y = size.height - (sample.gear - minGear).toFloat() / span * size.height
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, previousY)
                    path.lineTo(x, y)
                }
                previousY = y
            }
            drawPath(path, color = gearColor, style = Stroke(width = 2f))
            drawCursor(cursorMs, lap.lapTimeMs, size, cursorColor)
        }
    }
}

@Composable
fun TrackMap(
    samples: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    modifier: Modifier = Modifier,
) {
    val window = remember(samples, lap) { samples.lapWindow(lap) }
    if (window.size < 2) {
        Text("No track positions for this lap", modifier = modifier)
        return
    }
    val points = window.map { it.positionX to it.positionZ }
    val minX = points.minOf { it.first }
    val maxX = points.maxOf { it.first }
    val minZ = points.minOf { it.second }
    val maxZ = points.maxOf { it.second }
    val current = remember(window, lap, cursorMs) { window.nearest(lap, cursorMs) }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxWidth().height(240.dp)) {
        val spanX = (maxX - minX).coerceAtLeast(0.001f)
        val spanZ = (maxZ - minZ).coerceAtLeast(0.001f)
        val scale = minOf(size.width / spanX, size.height / spanZ) * 0.9f
        val offsetX = (size.width - spanX * scale) / 2f
        val offsetY = (size.height - spanZ * scale) / 2f
        fun mapPoint(sample: Sample): Offset = Offset(
            offsetX + (sample.positionX - minX) * scale,
            offsetY + (sample.positionZ - minZ) * scale,
        )

        val path = Path()
        val step = ceil(window.size / 400f).toInt().coerceAtLeast(1)
        var first = true
        for (index in window.indices step step) {
            val point = mapPoint(window[index])
            if (first) {
                path.moveTo(point.x, point.y)
                first = false
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f))
        current?.let {
            val point = mapPoint(it)
            drawCircle(color = dotColor, radius = 7f, center = point)
        }
    }
}

private fun timeToX(timeMs: Int, lapTimeMs: Int, width: Float): Float =
    (timeMs.toFloat() / lapTimeMs.coerceAtLeast(1)).coerceIn(0f, 1f) * width

private fun DrawScope.drawCursor(
    cursorMs: Int,
    lapTimeMs: Int,
    size: Size,
    color: Color,
) {
    val x = timeToX(cursorMs, lapTimeMs, size.width)
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 2f,
    )
}
