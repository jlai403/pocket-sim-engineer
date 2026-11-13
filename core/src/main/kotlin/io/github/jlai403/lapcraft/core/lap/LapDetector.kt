package io.github.jlai403.lapcraft.core.lap

import io.github.jlai403.lapcraft.core.model.Sample
import kotlin.math.abs

data class LapBoundary(
    val lapNumber: Int,
    val startNanos: Long,
    val endNanos: Long,
    val lapTimeMs: Int,
)

class LapDetector {
    private var previousLapCount: Int? = null
    private var lapStartNanos: Long = 0
    private var lastPacketId: Int? = null
    private var lastTimeNanos: Long = Long.MIN_VALUE

    /** Start of the lap in progress, or null before the first sample. */
    @Volatile
    var currentLapStartNanos: Long? = null
        private set

    /** True only when a lap count increase proved the start/finish crossing was observed. */
    @Volatile
    var currentLapStartObserved: Boolean = false
        private set

    fun onSample(sample: Sample): LapBoundary? {
        if (sample.packetId == lastPacketId) return null
        if (sample.timeNanos <= lastTimeNanos) return null

        val previous = previousLapCount
        if (previous == null) {
            previousLapCount = sample.lapCount
            lapStartNanos = sample.timeNanos
            currentLapStartNanos = sample.timeNanos
            currentLapStartObserved = false
            lastPacketId = sample.packetId
            lastTimeNanos = sample.timeNanos
            return null
        }

        if (sample.lapCount < previous) {
            previousLapCount = sample.lapCount
            lapStartNanos = sample.timeNanos
            currentLapStartNanos = sample.timeNanos
            currentLapStartObserved = false
            lastPacketId = sample.packetId
            lastTimeNanos = sample.timeNanos
            return null
        }

        var boundary: LapBoundary? = null
        if (sample.lapCount > previous) {
            val lapNumber = sample.lapCount - 1
            if (lapNumber > 0) {
                val measuredMs = ((sample.timeNanos - lapStartNanos) / 1_000_000L).toInt()
                val lapTimeMs = if (sample.lastLapTimeMs > 0) {
                    sample.lastLapTimeMs
                } else {
                    measuredMs
                }
                // A clock jump, or arming mid-lap, means this lap has no complete telemetry.
                val complete = sample.lastLapTimeMs <= 0 ||
                    abs(measuredMs - sample.lastLapTimeMs) <= LAP_SPAN_TOLERANCE_MS
                if (complete && lapTimeMs in MIN_PLAUSIBLE_LAP_MS..MAX_PLAUSIBLE_LAP_MS) {
                    boundary = LapBoundary(
                        lapNumber = lapNumber,
                        startNanos = lapStartNanos,
                        endNanos = sample.timeNanos,
                        lapTimeMs = lapTimeMs,
                    )
                }
            }
            lapStartNanos = sample.timeNanos
            currentLapStartNanos = sample.timeNanos
            currentLapStartObserved = true
        }

        previousLapCount = sample.lapCount
        lastPacketId = sample.packetId
        lastTimeNanos = sample.timeNanos
        return boundary
    }

    private companion object {
        const val MIN_PLAUSIBLE_LAP_MS = 10_000
        const val MAX_PLAUSIBLE_LAP_MS = 1_800_000
        const val LAP_SPAN_TOLERANCE_MS = 2_000
    }
}
