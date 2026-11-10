package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.clock.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingControllerTest {
    private class FakeClock(var now: Long = 0) : Clock {
        override fun nanoTime() = now
    }

    private class FakeRecorder : Recorder {
        var started = false
        var stopped = false
        override val isRecording get() = started && !stopped
        override fun start() {
            started = true
            stopped = false
        }
        override fun stop() {
            stopped = true
        }
    }

    @Test
    fun `arms and starts on car on track`() {
        val controller = RecordingController(FakeClock(), FakeRecorder())
        controller.onEvent(RecordingEvent.Arm)
        assertEquals(RecordingState.Armed, controller.state.value)
        controller.onEvent(RecordingEvent.PermissionGranted)
        assertEquals(RecordingState.Armed, controller.state.value)
        controller.onEvent(RecordingEvent.CarOnTrack(1, 3474))
        assertEquals(RecordingState.Recording(1, 3474), controller.state.value)
    }

    @Test
    fun `stops after five seconds off track`() {
        val clock = FakeClock()
        val recorder = FakeRecorder()
        val controller = RecordingController(clock, recorder)
        controller.onEvent(RecordingEvent.Arm)
        controller.onEvent(RecordingEvent.PermissionGranted)
        controller.onEvent(RecordingEvent.CarOnTrack(1, 3474))
        controller.onEvent(RecordingEvent.OffTrack)

        clock.now = 4_999_999_999
        controller.tick()
        assertEquals(RecordingState.Recording(1, 3474), controller.state.value)
        assertFalse(recorder.stopped)

        clock.now = 5_000_000_000
        controller.tick()
        assertEquals(RecordingState.Idle, controller.state.value)
        assertTrue(recorder.stopped)
    }

    @Test
    fun `car change and stop end recording immediately`() {
        val recorder = FakeRecorder()
        val controller = RecordingController(FakeClock(), recorder)
        controller.onEvent(RecordingEvent.Arm)
        controller.onEvent(RecordingEvent.PermissionGranted)
        controller.onEvent(RecordingEvent.CarOnTrack(1, 3474))
        controller.onEvent(RecordingEvent.CarChanged)
        assertEquals(RecordingState.Idle, controller.state.value)
        assertTrue(recorder.stopped)
    }

    @Test
    fun `permission denial never starts the recorder`() {
        val recorder = FakeRecorder()
        val controller = RecordingController(FakeClock(), recorder)
        controller.onEvent(RecordingEvent.Arm)
        controller.onEvent(RecordingEvent.PermissionDenied)
        assertEquals(RecordingState.Error("permission-denied"), controller.state.value)
        assertFalse(recorder.started)
    }
}
