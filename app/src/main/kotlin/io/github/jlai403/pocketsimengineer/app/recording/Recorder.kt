package io.github.jlai403.pocketsimengineer.app.recording

interface Recorder {
    val isRecording: Boolean
    fun start()
    fun stop()
}
