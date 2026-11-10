package io.github.jlai403.pocketsimengineer.app.recording

data class VideoEncoderConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrate: Int = 10_000_000,
    val mime: String = "video/avc",
)
