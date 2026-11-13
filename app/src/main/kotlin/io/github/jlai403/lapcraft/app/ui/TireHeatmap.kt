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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TireTile("FL", fl, imperial)
            TireTile("FR", fr, imperial)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TireTile("RL", rl, imperial)
            TireTile("RR", rr, imperial)
        }
    }
}

@Composable
private fun TireTile(label: String, celsius: Float, imperial: Boolean) {
    val shown = if (imperial) celsius * 9f / 5f + 32f else celsius
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tireTemperatureColor(celsius)),
        contentAlignment = Alignment.Center,
    ) {
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
