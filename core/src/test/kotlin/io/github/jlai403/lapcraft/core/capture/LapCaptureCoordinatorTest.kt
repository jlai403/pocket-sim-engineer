package io.github.jlai403.lapcraft.core.capture

import io.github.jlai403.lapcraft.core.lap.LapBoundary
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.SessionMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LapCaptureCoordinatorTest {
    private class RecordingSink : LapSink {
        val saved = mutableListOf<Pair<LapBoundary, List<Sample>>>()

        override fun save(boundary: LapBoundary, samples: List<Sample>): SessionMetadata {
            saved += boundary to samples
            return SessionMetadata(
                sessionId = "s1",
                createdAt = "2026-11-11T10:00:00Z",
                trackLabel = "Unknown",
                carCode = 0,
                carName = "Car #0",
                rawFile = "",
            )
        }
    }

    private fun sample(atMs: Long, lapCount: Int = 1) = Sample(
        timeNanos = atMs * 1_000_000,
        speedMps = 0f,
        throttle = 0,
        brake = 0,
        gear = 1,
        rpm = 0f,
        positionX = 0f,
        positionY = 0f,
        positionZ = 0f,
        lapCount = lapCount,
        bestLapTimeMs = 0,
        lastLapTimeMs = 0,
        flags = 0,
        carCode = 0,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = 0,
    )

    private fun boundary(startMs: Long, endMs: Long, lap: Int = 1) = LapBoundary(
        lapNumber = lap,
        startNanos = startMs * 1_000_000,
        endNanos = endMs * 1_000_000,
        lapTimeMs = (endMs - startMs).toInt(),
    )

    @Test
    fun `capture saves the lap whose start was requested`() {
        val sink = RecordingSink()
        val coordinator = LapCaptureCoordinator(sink = sink)
        coordinator.requestCapture(expectedLapStartNanos = 1_000_000_000L)
        (0..120).forEach { coordinator.onSample(sample(atMs = it * 1_000L)) }
        coordinator.onBoundary(boundary(startMs = 1_000, endMs = 120_000))

        assertEquals(1, sink.saved.size)
        assertTrue(coordinator.state.value is CaptureState.Saved)
    }

    @Test
    fun `capture requested after connecting mid-lap rejects the partial lap`() {
        val sink = RecordingSink()
        val coordinator = LapCaptureCoordinator(sink = sink)
        coordinator.onSample(sample(atMs = 30_000))
        coordinator.requestCapture(expectedLapStartNanos = 30_000_000_000L)
        coordinator.onSample(sample(atMs = 31_000))
        coordinator.onBoundary(boundary(startMs = 30_000, endMs = 31_000))

        assertTrue(sink.saved.isEmpty())
        assertTrue(coordinator.state.value is CaptureState.Rejected)
    }

    @Test
    fun `boundary for a different lap start is rejected`() {
        val sink = RecordingSink()
        val coordinator = LapCaptureCoordinator(sink = sink)
        coordinator.requestCapture(expectedLapStartNanos = 999_000_000L)
        coordinator.onSample(sample(atMs = 1_000))
        coordinator.onBoundary(boundary(startMs = 1_000, endMs = 60_000))

        assertTrue(sink.saved.isEmpty())
        assertTrue(coordinator.state.value is CaptureState.Rejected)
    }

    @Test
    fun `cancel and disconnect discard the capture`() {
        val sink = RecordingSink()
        val coordinator = LapCaptureCoordinator(sink = sink)
        coordinator.requestCapture(expectedLapStartNanos = 1_000_000_000L)
        coordinator.cancelCapture()
        coordinator.onSample(sample(atMs = 1_000))
        coordinator.onBoundary(boundary(startMs = 1_000, endMs = 2_000))
        assertTrue(sink.saved.isEmpty())

        coordinator.requestCapture(expectedLapStartNanos = 2_000_000_000L)
        coordinator.onDisconnect()
        assertEquals(CaptureState.Idle, coordinator.state.value)
    }
}
