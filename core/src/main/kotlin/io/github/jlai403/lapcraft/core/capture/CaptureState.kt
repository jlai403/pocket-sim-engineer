package io.github.jlai403.lapcraft.core.capture

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Capturing : CaptureState
    data class Saved(val sessionId: String, val lapTimeMs: Int) : CaptureState
    data class Rejected(val reason: String) : CaptureState
}
