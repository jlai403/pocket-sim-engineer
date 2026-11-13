package io.github.jlai403.lapcraft.core.capture

import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.SessionMetadata
import kotlinx.coroutines.flow.MutableStateFlow

fun interface LapSink {
    fun save(boundary: LapBoundary, samples: List<Sample>): SessionMetadata
}

/**
 * Captures exactly one lap. The caller requests the lap currently in progress and passes the
 * start crossing it observed; the next boundary that matches that start is saved.
 */
class LapCaptureCoordinator(
    private val buffer: LapSampleBuffer = LapSampleBuffer(),
    private val sink: LapSink,
) {
    val state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    private var expectedStartNanos: Long? = null

    fun requestCapture(expectedLapStartNanos: Long) {
        expectedStartNanos = expectedLapStartNanos
        state.value = CaptureState.Capturing
    }

    fun cancelCapture() {
        expectedStartNanos = null
        state.value = CaptureState.Idle
    }

    fun onSample(sample: Sample) {
        buffer.add(sample)
    }

    @Synchronized
    fun onBoundary(boundary: LapBoundary) {
        val expected = expectedStartNanos
        if (state.value !is CaptureState.Capturing) {
            buffer.trimBefore(boundary.endNanos - KEEP_NANOS)
            return
        }
        if (boundary.startNanos != expected || !buffer.covers(boundary)) {
            expectedStartNanos = null
            state.value = CaptureState.Rejected("Lap started before the app connected")
            buffer.trimBefore(boundary.endNanos - KEEP_NANOS)
            return
        }
        val saved = sink.save(boundary, buffer.samplesFor(boundary))
        expectedStartNanos = null
        state.value = CaptureState.Saved(saved.sessionId, boundary.lapTimeMs)
        buffer.trimBefore(boundary.endNanos - KEEP_NANOS)
    }

    fun onDisconnect() {
        expectedStartNanos = null
        buffer.clear()
        if (state.value is CaptureState.Capturing) state.value = CaptureState.Idle
    }

    private companion object {
        const val KEEP_NANOS = 3_000_000_000L
    }
}
