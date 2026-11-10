package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.clock.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordingController(
    private val clock: Clock,
    private val recorder: Recorder,
) {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var offTrackSinceNanos: Long? = null
    private var currentCarCode: Int? = null
    private var recording = false

    @Synchronized
    fun onEvent(event: RecordingEvent) {
        when (event) {
            RecordingEvent.Arm -> {
                stopRecorder()
                _state.value = RecordingState.Armed
            }

            RecordingEvent.PermissionGranted -> {
                if (_state.value is RecordingState.Armed) {
                    _state.value = RecordingState.Armed
                }
            }

            RecordingEvent.PermissionDenied -> {
                stopRecorder()
                _state.value = RecordingState.Error("permission-denied")
            }

            is RecordingEvent.CarOnTrack -> {
                val state = _state.value
                if (state !is RecordingState.Armed && state !is RecordingState.Recording) return
                if (recording && currentCarCode != null && currentCarCode != event.carCode) {
                    stopAndIdle()
                    return
                }
                if (!recording) {
                    recorder.start()
                    recording = true
                }
                currentCarCode = event.carCode
                offTrackSinceNanos = null
                _state.value = RecordingState.Recording(event.lapCount, event.carCode)
            }

            RecordingEvent.OffTrack, RecordingEvent.NoPackets -> {
                if (recording && offTrackSinceNanos == null) {
                    offTrackSinceNanos = clock.nanoTime()
                }
            }

            RecordingEvent.Stop, RecordingEvent.CarChanged -> stopAndIdle()

            is RecordingEvent.RecorderError -> {
                stopRecorder()
                _state.value = RecordingState.Error(event.message)
            }
        }
    }

    @Synchronized
    fun tick() {
        val since = offTrackSinceNanos ?: return
        if (recording && clock.nanoTime() - since >= 5_000_000_000L) {
            stopAndIdle()
        }
    }

    private fun stopAndIdle() {
        stopRecorder()
        _state.value = RecordingState.Idle
    }

    private fun stopRecorder() {
        if (recording) {
            recorder.stop()
            recording = false
        }
        currentCarCode = null
        offTrackSinceNanos = null
    }
}
