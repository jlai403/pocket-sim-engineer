package io.github.jlai403.lapcraft.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapMetadata
import kotlin.math.abs

/** One line under a compared video: pedal bars, tyre temperatures, and the steering angle. */
@Composable
internal fun CompareReadoutRow(
    samples: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    imperial: Boolean,
    modifier: Modifier = Modifier,
) {
    val window = remember(samples, lap) { samples.lapWindow(lap) }
    val sample = remember(window, lap, cursorMs) { window.sampleAt(lap, cursorMs) }
    if (sample == null) {
        Box(
            modifier = modifier.fillMaxWidth().height(ReadoutHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "No telemetry",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val rpmMax = remember(window) { window.maxOf { it.rpm }.coerceAtLeast(1f) }
    val hasSteering = remember(window) { window.any { abs(it.steering) > 0.001f } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ReadoutHeight)
            .padding(end = 4.dp)
            .testTag("compare-readout"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniBar(
            label = "THR",
            fraction = sample.throttle / 255f,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(BarWidth),
        )
        MiniBar(
            label = "BRK",
            fraction = sample.brake / 255f,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.width(BarWidth),
        )
        MiniBar(
            label = "RPM",
            fraction = sample.rpm / rpmMax,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.width(BarWidth),
        )
        Spacer(Modifier.weight(1f))
        TireHeatmap(
            fl = sample.tyreTempFL,
            fr = sample.tyreTempFR,
            rl = sample.tyreTempRL,
            rr = sample.tyreTempRR,
            imperial = imperial,
            compact = true,
        )
        if (hasSteering) {
            Box(
                modifier = Modifier.size(ReadoutHeight),
                contentAlignment = Alignment.Center,
            ) {
                SteeringWheel(steering = sample.steering, wheelSize = 26.dp)
            }
        }
    }
}

@Composable
private fun MiniBar(
    label: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
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
}

private val ReadoutHeight = 30.dp
private val BarWidth = 56.dp
