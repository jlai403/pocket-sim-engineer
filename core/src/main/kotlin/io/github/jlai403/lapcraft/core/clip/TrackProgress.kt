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
    private const val PATH_STEP_M = 2.5
    private const val WINDOW_BEHIND_M = 10.0
    private const val WINDOW_AHEAD_M = 30.0
    private const val OFF_PATH_M = 50.0

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

    /**
     * Puts [reference] on [primary]'s driven path, so the same corner is compared with the same
     * corner even when the two lines differ. Null when either lap cannot form a path.
     */
    fun align(primary: List<Sample>, reference: List<Sample>, referenceStart: Long): Alignment? {
        val path = Path.of(primary) ?: return null
        if (reference.size < 2) return null
        val axis = FloatArray(reference.size)
        val times = IntArray(reference.size)
        var previousMetres = 0.0
        for (index in reference.indices) {
            val sample = reference[index]
            times[index] = ((sample.timeNanos - referenceStart) / 1_000_000L).toInt()
            val advance = if (index == 0) {
                0.0
            } else {
                val previous = reference[index - 1]
                hypot(
                    sample.positionX.toDouble() - previous.positionX.toDouble(),
                    sample.positionZ.toDouble() - previous.positionZ.toDouble(),
                ).takeIf { it <= MAX_STEP_M } ?: 0.0
            }
            val metres = path
                .project(sample.positionX.toDouble(), sample.positionZ.toDouble(), previousMetres, advance)
                .coerceAtLeast(previousMetres)
            previousMetres = metres
            axis[index] = (metres / path.total * 100.0).toFloat().coerceIn(0f, 100f)
        }
        return Alignment(axis, times)
    }

    /** Where each reference sample sits on the primary path, and the way back to its clock. */
    class Alignment internal constructor(
        private val axis: FloatArray,
        private val timesMs: IntArray,
    ) {
        /** Position of reference sample [index] along the primary lap's path, 0–100. */
        fun axisPercentOfSample(index: Int): Float = axis[index]

        /** Reference lap time at a position along the primary lap's path. */
        fun referenceTimeAt(percent: Float, lapTimeMs: Int): Int {
            if (axis.isEmpty()) return 0
            if (percent <= axis[0]) return timesMs[0]
            if (percent >= axis[axis.lastIndex]) return lapTimeMs
            var low = 0
            var high = axis.lastIndex
            while (low < high) {
                val mid = (low + high) ushr 1
                if (axis[mid] < percent) low = mid + 1 else high = mid
            }
            val span = axis[low] - axis[low - 1]
            if (span <= 0f) return timesMs[low]
            val fraction = (percent - axis[low - 1]) / span
            val ms = timesMs[low - 1] + (timesMs[low] - timesMs[low - 1]) * fraction
            return ms.roundToInt().coerceIn(0, lapTimeMs)
        }
    }

    /** A lap's driven positions resampled every [PATH_STEP_M] metres. */
    private class Path(
        private val xs: DoubleArray,
        private val zs: DoubleArray,
        private val metres: DoubleArray,
    ) {
        val total: Double get() = metres.last()

        /**
         * Distance along the path of the point closest to (x, z), searched between
         * [WINDOW_BEHIND_M] behind and [WINDOW_AHEAD_M] plus twice [advance] ahead of
         * [fromMetres], so a track that crosses itself cannot capture the car on the
         * other branch. A window result further than [OFF_PATH_M] off the path is kept
         * unless the whole path holds a point at least twice as close.
         */
        fun project(x: Double, z: Double, fromMetres: Double, advance: Double): Double {
            val near = nearest(
                x = x,
                z = z,
                from = fromMetres - WINDOW_BEHIND_M,
                to = fromMetres + WINDOW_AHEAD_M + 2 * advance,
            )
            if (near.distance <= OFF_PATH_M) return near.metres
            val anywhere = nearest(x, z, 0.0, total)
            return if (anywhere.distance * 2 <= near.distance) anywhere.metres else near.metres
        }

        private class Hit(val distance: Double, val metres: Double)

        private fun nearest(x: Double, z: Double, from: Double, to: Double): Hit {
            val first = segmentAt(from.coerceIn(0.0, total))
            val last = segmentAt(to.coerceIn(0.0, total))
            var best = Hit(Double.MAX_VALUE, metres[first])
            for (index in first..last) {
                val startX = xs[index]
                val startZ = zs[index]
                val deltaX = xs[index + 1] - startX
                val deltaZ = zs[index + 1] - startZ
                val span = deltaX * deltaX + deltaZ * deltaZ
                val t = if (span <= 0.0) {
                    0.0
                } else {
                    (((x - startX) * deltaX + (z - startZ) * deltaZ) / span).coerceIn(0.0, 1.0)
                }
                val distance = hypot(x - (startX + deltaX * t), z - (startZ + deltaZ * t))
                if (distance < best.distance) {
                    best = Hit(distance, metres[index] + (metres[index + 1] - metres[index]) * t)
                }
            }
            return best
        }

        /** Index of the segment holding [at], clamped to the path. */
        private fun segmentAt(at: Double): Int {
            var low = 0
            var high = xs.size - 2
            while (low < high) {
                val mid = (low + high) ushr 1
                if (metres[mid + 1] <= at) low = mid + 1 else high = mid
            }
            return low
        }

        companion object {
            fun of(samples: List<Sample>): Path? {
                if (samples.size < 2) return null
                val xs = DoubleArray(samples.size) { samples[it].positionX.toDouble() }
                val zs = DoubleArray(samples.size) { samples[it].positionZ.toDouble() }
                val cumulative = DoubleArray(samples.size)
                var total = 0.0
                for (index in 1 until samples.size) {
                    val step = hypot(xs[index] - xs[index - 1], zs[index] - zs[index - 1])
                    if (step > MAX_STEP_M) return null
                    total += step
                    cumulative[index] = total
                }
                if (total <= 0.0) return null

                val limit = (total / PATH_STEP_M).toInt() + 2
                val vertexX = DoubleArray(limit)
                val vertexZ = DoubleArray(limit)
                val vertexM = DoubleArray(limit)
                vertexX[0] = xs[0]
                vertexZ[0] = zs[0]
                var count = 1
                var target = PATH_STEP_M
                for (index in 1 until samples.size) {
                    val start = cumulative[index - 1]
                    val span = cumulative[index] - start
                    if (span <= 0.0) continue
                    while (target <= cumulative[index] && count < limit) {
                        val fraction = (target - start) / span
                        vertexX[count] = xs[index - 1] + (xs[index] - xs[index - 1]) * fraction
                        vertexZ[count] = zs[index - 1] + (zs[index] - zs[index - 1]) * fraction
                        vertexM[count] = target
                        count++
                        target += PATH_STEP_M
                    }
                }
                if (vertexM[count - 1] < total) {
                    vertexX[count] = xs.last()
                    vertexZ[count] = zs.last()
                    vertexM[count] = total
                    count++
                }
                return Path(
                    xs = vertexX.copyOf(count),
                    zs = vertexZ.copyOf(count),
                    metres = vertexM.copyOf(count),
                )
            }
        }
    }
}
