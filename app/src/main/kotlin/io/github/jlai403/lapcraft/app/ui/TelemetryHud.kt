package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapMetadata

private const val TRAILING_WINDOW_MS = 2_000

/** Throttle and brake over the last two seconds before the cursor. */
@Composable
internal fun TelemetryHud(
    window: List<Sample>,
    lap: LapMetadata,
    cursorMs: Int,
    modifier: Modifier = Modifier,
) {
    val context = remember(window, lap, cursorMs) {
        window.windowEndingAt(lap, cursorMs, TRAILING_WINDOW_MS)
    }
    if (context.isEmpty()) return
    val throttleColor = MaterialTheme.colorScheme.primary
    val brakeColor = MaterialTheme.colorScheme.error
    Box(
        modifier = modifier
            .testTag("telemetry-hud")
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.width(140.dp).height(20.dp)) {
            TelemetrySparkline(
                window = context,
                lap = lap,
                valueOf = { it.throttle.toFloat() },
                maxValue = 255f,
                color = throttleColor,
                modifier = Modifier.matchParentSize(),
            )
            TelemetrySparkline(
                window = context,
                lap = lap,
                valueOf = { it.brake.toFloat() },
                maxValue = 255f,
                color = brakeColor,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
