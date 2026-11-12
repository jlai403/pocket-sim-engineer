package io.github.jlai403.pocketsimengineer.core.clip

import io.github.jlai403.pocketsimengineer.core.model.Sample
import kotlin.math.abs
import kotlin.math.hypot

/** Percentage of the lap completed at a lap-relative telemetry time. */
object TrackProgress {
    private const val MAX_STEP_M = 200.0

    fun percentAt(samples: List<Sample>, lapStartNanos: Long, timeMs: Int): Float {
        if (samples.size < 2) return 0f

        var total = 0.0
        val cumulative = DoubleArray(samples.size)
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val step = hypot(
                current.positionX.toDouble() - previous.positionX.toDouble(),
                current.positionZ.toDouble() - previous.positionZ.toDouble(),
            )
            if (step <= MAX_STEP_M) total += step
            cumulative[index] = total
        }
        if (total <= 0.0) return 0f

        val target = lapStartNanos + timeMs * 1_000_000L
        val nearest = samples.indices.minByOrNull { abs(samples[it].timeNanos - target) } ?: return 0f
        return ((cumulative[nearest] / total) * 100.0).toFloat().coerceIn(0f, 100f)
    }
}
