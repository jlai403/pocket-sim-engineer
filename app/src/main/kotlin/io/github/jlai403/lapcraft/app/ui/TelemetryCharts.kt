package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapMetadata
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private data class TraceSeries(
    val color: Color,
    val valueOf: (Sample) -> Float,
    val maxValue: Float,
)

@Composable
fun ChartColumn(
    samples: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    imperial: Boolean = false,
    showSpeed: Boolean = true,
    showPedals: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val speedFactor = if (imperial) 2.23694f else 3.6f
    val window = remember(samples, lap) { samples.lapWindow(lap) }
    if (window.isEmpty()) {
        Text("No telemetry for this lap", modifier = modifier)
        return
    }
    val hasSteering = remember(window) { window.any { abs(it.steering) > 0.001f } }
    val current = remember(window, lap, cursorMs) { window.sampleAt(lap, cursorMs) }
    val speedColor = MaterialTheme.colorScheme.secondary
    val throttleColor = MaterialTheme.colorScheme.primary
    val brakeColor = MaterialTheme.colorScheme.error
    val rpmColor = MaterialTheme.colorScheme.tertiary
    val gearColor = MaterialTheme.colorScheme.tertiary
    val rpmMax = remember(window) { window.maxOf { it.rpm }.coerceAtLeast(1f) }
    val speedMax = remember(window, speedFactor) {
        window.maxOf { it.speedMps * speedFactor }.coerceAtLeast(1f)
    }
    val gearMax = remember(window) { window.maxOf { it.gear }.coerceAtLeast(1) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        current?.let { sample ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BarRow(
                        label = "THR",
                        fraction = sample.throttle / 255f,
                        value = "%d%%".format(sample.throttle * 100 / 255),
                        color = throttleColor,
                    )
                    BarRow(
                        label = "BRK",
                        fraction = sample.brake / 255f,
                        value = "%d%%".format(sample.brake * 100 / 255),
                        color = brakeColor,
                    )
                    BarRow(
                        label = "RPM",
                        fraction = sample.rpm / rpmMax,
                        value = "%.0f".format(sample.rpm),
                        color = rpmColor,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TireHeatmap(
                        fl = sample.tyreTempFL,
                        fr = sample.tyreTempFR,
                        rl = sample.tyreTempRL,
                        rr = sample.tyreTempRR,
                        imperial = imperial,
                    )
                    if (hasSteering) {
                        SteeringWheel(steering = sample.steering)
                        Text(
                            "STEER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (showPedals) {
            TraceChart(
                window = window,
                lap = lap,
                cursorMs = cursorMs,
                label = "Throttle / Brake",
                series = listOf(
                    TraceSeries(throttleColor, { it.throttle.toFloat() }, 255f),
                    TraceSeries(brakeColor, { it.brake.toFloat() }, 255f),
                ),
            )
        }
        if (showSpeed) {
            TraceChart(
                window = window,
                lap = lap,
                cursorMs = cursorMs,
                label = "Speed / Gear",
                series = listOf(
                    TraceSeries(speedColor, { it.speedMps * speedFactor }, speedMax),
                    TraceSeries(gearColor, { it.gear.toFloat() }, gearMax.toFloat()),
                ),
            )
        }
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String, color: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumericText(
                text = value,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        ValueBar(
            fraction = fraction,
            color = color,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

@Composable
private fun ValueBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

@Composable
private fun TraceChart(
    window: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    label: String,
    series: List<TraceSeries>,
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.onSurface
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            val step = ceil(window.size / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            series.forEach { line ->
                val path = Path()
                var first = true
                for (index in window.indices step step) {
                    val sample = window[index]
                    val x = timeToX(sample.lapTimeMs(lap), lap.lapTimeMs, size.width)
                    val y = size.height -
                        (line.valueOf(sample) / line.maxValue).coerceIn(0f, 1f) * size.height
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(path, color = line.color, style = Stroke(width = 2f))
            }
            drawCursor(cursorMs, lap.lapTimeMs, size, cursorColor)
        }
    }
}

@Composable
internal fun TrackMap(
    samples: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    ghost: GhostLap? = null,
    showGhost: Boolean = true,
    follow: Boolean = false,
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
    val current = remember(window, lap, cursorMs) { window.sampleAt(lap, cursorMs) }
    val ghostWindow = remember(ghost) {
        ghost?.let { it.samples.lapWindow(it.lap) } ?: emptyList()
    }
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val dotColor = MaterialTheme.colorScheme.secondary
    val brakeColor = MaterialTheme.colorScheme.error
    val throttleColor = MaterialTheme.colorScheme.primary
    val ghostColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val anchor = if (follow) current else null
    val heading = remember(anchor, window, lap, cursorMs) {
        if (anchor == null) {
            0f
        } else {
            val ahead = window.sampleAt(lap, cursorMs + 250) ?: anchor
            val dx = ahead.positionX - anchor.positionX
            val dz = ahead.positionZ - anchor.positionZ
            if (dx == 0f && dz == 0f) 0f else atan2(dz.toDouble(), dx.toDouble()).toFloat()
        }
    }

    Column(modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val spanX = (maxX - minX).coerceAtLeast(0.001f)
            val spanZ = (maxZ - minZ).coerceAtLeast(0.001f)
            val fitScale = minOf(size.width / spanX, size.height / spanZ) * 0.9f
            val scale = if (anchor != null) {
                val distance = anchor.speedMps.coerceAtLeast(0f) * 2f + 10f
                (minOf(size.width, size.height) / distance * 0.9f)
                    .coerceIn(fitScale * 1.5f, fitScale * 6f)
            } else {
                fitScale
            }
            val offsetX = (size.width - spanX * fitScale) / 2f
            val offsetY = (size.height - spanZ * fitScale) / 2f
            val rotation = -heading - PI.toFloat() / 2f
            val cosR = cos(rotation)
            val sinR = sin(rotation)

            fun mapPoint(sample: Sample): Offset = if (anchor == null) {
                Offset(
                    offsetX + (sample.positionX - minX) * scale,
                    offsetY + (sample.positionZ - minZ) * scale,
                )
            } else {
                val dx = sample.positionX - anchor.positionX
                val dz = sample.positionZ - anchor.positionZ
                Offset(
                    size.width / 2f + (dx * cosR - dz * sinR) * scale,
                    size.height * 0.78f + (dx * sinR + dz * cosR) * scale * 0.35f,
                )
            }

            if (showGhost && ghostWindow.size >= 2) {
                val ghostPath = Path()
                val ghostStep = ceil(ghostWindow.size / 500f).toInt().coerceAtLeast(1)
                var first = true
                for (index in ghostWindow.indices step ghostStep) {
                    val point = mapPoint(ghostWindow[index])
                    if (first) {
                        ghostPath.moveTo(point.x, point.y)
                        first = false
                    } else {
                        ghostPath.lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = ghostPath,
                    color = ghostColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        ),
                    ),
                )
            }

            val segmentStep = ceil(window.size / 500f).toInt().coerceAtLeast(1)
            for (index in segmentStep until window.size step segmentStep) {
                val previous = window[index - segmentStep]
                val sample = window[index]
                val color = when {
                    sample.brake > 0 -> lerp(trackColor, brakeColor, sample.brake / 255f)
                    sample.throttle > 0 -> lerp(trackColor, throttleColor, sample.throttle / 255f)
                    else -> trackColor
                }
                drawLine(
                    color = color,
                    start = mapPoint(previous),
                    end = mapPoint(sample),
                    strokeWidth = 3.dp.toPx(),
                )
            }

            current?.let {
                drawCircle(color = dotColor, radius = 12f, center = mapPoint(it))
            }
        }
    }
}

@Composable
internal fun TrackMapLegend(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem("Brake", MaterialTheme.colorScheme.error)
        LegendItem("Throttle", MaterialTheme.colorScheme.primary)
        LegendItem("Ghost", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun timeToX(timeMs: Int, lapTimeMs: Int, width: Float): Float =
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
        strokeWidth = 3f,
    )
}
