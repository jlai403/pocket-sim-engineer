package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** F1-style wheel that turns with the recorded steering angle. */
@Composable
internal fun SteeringWheel(steering: Float, modifier: Modifier = Modifier) {
    val rim = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val hub = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .size(40.dp)
            .testTag("steering-wheel")
            .semantics { contentDescription = steeringDescription(steering) }
            .rotate(steeringWheelDegrees(steering)),
    ) {
        val w = size.width
        val h = size.height
        val rimPath = Path().apply {
            moveTo(0.14f * w, 0.74f * h)
            cubicTo(0.03f * w, 0.56f * h, 0.03f * w, 0.30f * h, 0.18f * w, 0.22f * h)
            cubicTo(0.36f * w, 0.12f * h, 0.64f * w, 0.12f * h, 0.82f * w, 0.22f * h)
            cubicTo(0.97f * w, 0.30f * h, 0.97f * w, 0.56f * h, 0.86f * w, 0.74f * h)
            lineTo(0.66f * w, 0.74f * h)
            cubicTo(0.62f * w, 0.58f * h, 0.57f * w, 0.50f * h, 0.50f * w, 0.50f * h)
            cubicTo(0.43f * w, 0.50f * h, 0.38f * w, 0.58f * h, 0.34f * w, 0.74f * h)
            close()
        }
        drawPath(
            path = rimPath,
            color = rim,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = hub,
            topLeft = Offset(0.35f * w, 0.40f * h),
            size = Size(0.30f * w, 0.20f * h),
            cornerRadius = CornerRadius(0.04f * w),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.9f),
            topLeft = Offset(0.41f * w, 0.44f * h),
            size = Size(0.18f * w, 0.05f * h),
            cornerRadius = CornerRadius(0.02f * w),
        )
        repeat(5) { index ->
            drawCircle(
                color = if (index == 2) accent else rim.copy(alpha = 0.35f),
                radius = 0.018f * w,
                center = Offset((0.40f + index * 0.05f) * w, 0.20f * h),
            )
        }
    }
}

/** Compose rotates clockwise for positive degrees; GT7 signs a left turn positive. */
internal fun steeringWheelDegrees(steering: Float): Float =
    -steering.coerceIn(-1f, 1f) * 180f

internal fun steeringDescription(steering: Float): String {
    val degrees = (abs(steering).coerceAtMost(1f) * 180f).roundToInt()
    return when {
        degrees < 2 -> "Steering wheel centred"
        steering > 0 -> "Steering wheel $degrees degrees left"
        else -> "Steering wheel $degrees degrees right"
    }
}
