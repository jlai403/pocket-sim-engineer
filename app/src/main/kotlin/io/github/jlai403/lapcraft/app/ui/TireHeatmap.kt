package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

private val TireHot = Color(0xFFFF6B6B)

/** Cold blue at 60 °C, optimal green at 90 °C, hot red at 120 °C. */
internal fun tireTemperatureColor(celsius: Float): Color = when {
    celsius <= 60f -> AccentBlue
    celsius >= 120f -> TireHot
    celsius < 90f -> lerp(AccentBlue, AccentGreen, (celsius - 60f) / 30f)
    else -> lerp(AccentGreen, TireHot, (celsius - 90f) / 30f)
}

@Composable
internal fun TireHeatmap(
    fl: Float,
    fr: Float,
    rl: Float,
    rr: Float,
    imperial: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val gap = if (compact) 2.dp else 3.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            TireTile("FL", fl, imperial, compact)
            TireTile("FR", fr, imperial, compact)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            TireTile("RL", rl, imperial, compact)
            TireTile("RR", rr, imperial, compact)
        }
    }
}

@Composable
private fun TireTile(label: String, celsius: Float, imperial: Boolean, compact: Boolean) {
    val shown = if (imperial) celsius * 9f / 5f + 32f else celsius
    Box(
        modifier = Modifier
            .size(if (compact) 18.dp else 32.dp)
            .clip(RoundedCornerShape(if (compact) 5.dp else 8.dp))
            .background(tireTemperatureColor(celsius)),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black.copy(alpha = 0.7f),
                maxLines = 1,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.7f),
                )
                NumericText(
                    text = "%.0f°".format(shown),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.75f),
                )
            }
        }
    }
}
