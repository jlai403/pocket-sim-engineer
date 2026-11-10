package io.github.jlai403.pocketsimengineer.core.lap

import io.github.jlai403.pocketsimengineer.core.model.Sample

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

    fun onSample(sample: Sample): LapBoundary? {
        if (sample.packetId == lastPacketId) return null
        if (sample.timeNanos <= lastTimeNanos) return null

        val previous = previousLapCount
        if (previous == null) {
            previousLapCount = sample.lapCount
            lapStartNanos = sample.timeNanos
            lastPacketId = sample.packetId
            lastTimeNanos = sample.timeNanos
            return null
        }

        if (sample.lapCount < previous) {
            previousLapCount = sample.lapCount
            lapStartNanos = sample.timeNanos
            lastPacketId = sample.packetId
            lastTimeNanos = sample.timeNanos
            return null
        }

        var boundary: LapBoundary? = null
        if (sample.lapCount > previous) {
            val lapNumber = sample.lapCount - 1
            if (lapNumber > 0) {
                val lapTimeMs = if (sample.lastLapTimeMs > 0) {
                    sample.lastLapTimeMs
                } else {
                    ((sample.timeNanos - lapStartNanos) / 1_000_000L).toInt()
                }
                if (lapTimeMs in MIN_PLAUSIBLE_LAP_MS..MAX_PLAUSIBLE_LAP_MS) {
                    boundary = LapBoundary(
                        lapNumber = lapNumber,
                        startNanos = lapStartNanos,
                        endNanos = sample.timeNanos,
                        lapTimeMs = lapTimeMs,
                    )
                }
            }
            lapStartNanos = sample.timeNanos
        }

        previousLapCount = sample.lapCount
        lastPacketId = sample.packetId
        lastTimeNanos = sample.timeNanos
        return boundary
    }

    private companion object {
        const val MIN_PLAUSIBLE_LAP_MS = 10_000
        const val MAX_PLAUSIBLE_LAP_MS = 1_800_000
    }
}
