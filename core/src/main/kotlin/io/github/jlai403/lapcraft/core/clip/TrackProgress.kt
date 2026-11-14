package io.github.jlai403.lapcraft.core.clip

import io.github.jlai403.lapcraft.core.model.Sample
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Cumulative distance along the racing line, expressed as a percentage of the lap.
 * Time and progress are interpolated between samples, so both directions are
 * continuous: a one-frame cursor step never jumps the reference lap.
 */
object TrackProgress {
    private const val MAX_STEP_M = 200.0

    /** A lap's distance table, built once and reused for every cursor move. */
    class Table internal constructor(
        private val timesMs: IntArray,
        private val cumulative: DoubleArray,
        private val totalMeters: Double,
    ) {
        /** Track progress of one sample, 0–100. */
        fun percentOfSample(index: Int): Float =
            ((cumulative[index] / totalMeters) * 100.0).toFloat().coerceIn(0f, 100f)

        /** Track progress at a lap-relative time, interpolated between samples. */
        fun percentAt(timeMs: Int): Float {
            if (timeMs <= timesMs.first()) return 0f
            if (timeMs >= timesMs.last()) return 100f
            return percentAtDistance(distanceAt(insertionIndex(timeMs), timeMs))
        }

        /** Lap-relative time at a track progress, interpolated between samples. */
        fun timeAtPercent(percent: Float, lapTimeMs: Int): Int {
            if (percent <= 0f) return 0
            if (percent >= 100f) return lapTimeMs
            val target = percent / 100.0 * totalMeters
            val high = insertionDistance(target)
            val prev = high - 1
            val span = cumulative[high] - cumulative[prev]
            val fraction = if (span <= 0.0) 0.0 else (target - cumulative[prev]) / span
            val ms = timesMs[prev] + (timesMs[high] - timesMs[prev]) * fraction
            return ms.roundToInt().coerceIn(0, lapTimeMs)
        }

        /** First sample index at or after [timeMs]; always within 1 until size. */
        private fun insertionIndex(timeMs: Int): Int {
            var low = 0
            var high = timesMs.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (timesMs[mid] < timeMs) low = mid + 1 else high = mid
            }
            return low
        }

        private fun distanceAt(high: Int, timeMs: Int): Double {
            val prev = high - 1
            val span = timesMs[high] - timesMs[prev]
            if (span <= 0) return cumulative[prev]
            val fraction = (timeMs - timesMs[prev]).toDouble() / span
            return cumulative[prev] + (cumulative[high] - cumulative[prev]) * fraction
        }

        /** First sample index whose cumulative distance reaches [target]; at least 1. */
        private fun insertionDistance(target: Double): Int {
            var low = 0
            var high = cumulative.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (cumulative[mid] < target) low = mid + 1 else high = mid
            }
            return low.coerceIn(1, cumulative.lastIndex)
        }

        private fun percentAtDistance(distance: Double): Float =
            ((distance / totalMeters) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    /** Distance table for [samples], or null when the lap never moved. */
    fun table(samples: List<Sample>, lapStartNanos: Long): Table? {
        if (samples.size < 2) return null
        val times = IntArray(samples.size)
        val cumulative = DoubleArray(samples.size)
        var total = 0.0
        for (index in samples.indices) {
            times[index] = ((samples[index].timeNanos - lapStartNanos) / 1_000_000L).toInt()
            if (index == 0) continue
            val previous = samples[index - 1]
            val current = samples[index]
            val step = hypot(
                current.positionX.toDouble() - previous.positionX.toDouble(),
                current.positionZ.toDouble() - previous.positionZ.toDouble(),
            )
            if (step <= MAX_STEP_M) total += step
            cumulative[index] = total
        }
        if (total <= 0.0) return null
        return Table(times, cumulative, total)
    }

    fun percentAt(samples: List<Sample>, lapStartNanos: Long, timeMs: Int): Float =
        table(samples, lapStartNanos)?.percentAt(timeMs) ?: 0f

    fun timeAtPercent(
        samples: List<Sample>,
        lapStartNanos: Long,
        percent: Float,
        lapTimeMs: Int,
    ): Int = table(samples, lapStartNanos)?.timeAtPercent(percent, lapTimeMs) ?: 0
}
