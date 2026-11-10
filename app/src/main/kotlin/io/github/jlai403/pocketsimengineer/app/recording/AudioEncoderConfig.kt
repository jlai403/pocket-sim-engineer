package io.github.jlai403.pocketsimengineer.app.recording

data class AudioEncoderConfig(
    val sampleRate: Int = 48_000,
    val channels: Int = 2,
    val bitrate: Int = 160_000,
    val mime: String = "audio/mp4a-latm",
)
