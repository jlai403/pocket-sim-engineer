package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata

/** Speed, gear, and ±5 s pedal traces over the replay. */
@Composable
internal fun TelemetryHud(
    window: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    imperial: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = remember(window, lap, cursorMs) { window.windowAround(lap, cursorMs, 5_000) }
    val current = context.sampleAt(lap, cursorMs) ?: return
    val speedFactor = if (imperial) 2.23694f else 3.6f
    val speedUnit = if (imperial) "mph" else "km/h"
    val throttleColor = MaterialTheme.colorScheme.primary
    val brakeColor = MaterialTheme.colorScheme.error
    Column(
        modifier = modifier
            .testTag("telemetry-hud")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumericText(
                "%.0f %s".format(current.speedMps * speedFactor, speedUnit),
                style = MaterialTheme.typography.labelLarge,
            )
            NumericText(
                "Gear %d".format(current.gear),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("THR", style = MaterialTheme.typography.labelSmall)
            TelemetrySparkline(
                window = context,
                lap = lap,
                valueOf = { it.throttle.toFloat() },
                maxValue = 255f,
                color = throttleColor,
                modifier = Modifier.width(64.dp).height(16.dp),
            )
            Text("BRK", style = MaterialTheme.typography.labelSmall)
            TelemetrySparkline(
                window = context,
                lap = lap,
                valueOf = { it.brake.toFloat() },
                maxValue = 255f,
                color = brakeColor,
                modifier = Modifier.width(64.dp).height(16.dp),
            )
        }
    }
}
