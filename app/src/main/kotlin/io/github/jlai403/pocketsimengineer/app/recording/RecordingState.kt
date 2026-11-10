package io.github.jlai403.pocketsimengineer.app.recording

sealed interface RecordingState {
    data object Idle : RecordingState
    data object Armed : RecordingState
    data class Recording(val lapCount: Int, val carCode: Int) : RecordingState
    data object Stopping : RecordingState
    data class Error(val message: String) : RecordingState
}
