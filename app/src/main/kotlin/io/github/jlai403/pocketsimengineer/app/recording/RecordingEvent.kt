package io.github.jlai403.pocketsimengineer.app.recording

sealed interface RecordingEvent {
    data object Arm : RecordingEvent
    data object PermissionGranted : RecordingEvent
    data object PermissionDenied : RecordingEvent
    data class CarOnTrack(val lapCount: Int, val carCode: Int) : RecordingEvent
    data object OffTrack : RecordingEvent
    data object NoPackets : RecordingEvent
    data object Stop : RecordingEvent
    data object CarChanged : RecordingEvent
    data class RecorderError(val message: String) : RecordingEvent
}
