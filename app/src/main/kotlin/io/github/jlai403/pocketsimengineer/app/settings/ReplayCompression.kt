package io.github.jlai403.pocketsimengineer.app.settings

import androidx.media3.common.MimeTypes

/** How new replay clips are stored. Defaults are the balanced, quality-first profile. */
enum class ReplayPreset(val label: String) {
    Original("Original"),
    Balanced("Balanced"),
    Compact("Compact"),
}

enum class ReplayKeyframes(val label: String, val seconds: Float) {
    Fast("Fast (0.5 s)", 0.5f),
    Standard("Standard (1 s)", 1f),
    Sparse("Sparse (2 s)", 2f),
}

enum class ReplayAudio(val label: String) {
    Stereo("Stereo 96 kbps"),
    Mono("Mono 64 kbps"),
    Remove("Remove audio"),
}

enum class ReplayResolution(val label: String, val width: Int, val height: Int) {
    FullHd("1080p", 1920, 1080),
    Hd("720p", 1280, 720),
}

enum class ReplayCodec(val label: String, val mime: String) {
    Hevc("HEVC", MimeTypes.VIDEO_H265),
    H264("H.264", MimeTypes.VIDEO_H264),
}

/** Encoder settings resolved from the user's choices. */
data class ReplayProfile(
    val transcode: Boolean,
    val width: Int,
    val height: Int,
    val videoMime: String,
    val videoBitrate: Int,
    val iFrameIntervalSeconds: Float,
    val audio: ReplayAudio,
    val audioBitrate: Int,
)

fun replayProfile(
    preset: ReplayPreset,
    keyframes: ReplayKeyframes,
    audio: ReplayAudio,
    resolution: ReplayResolution,
    codec: ReplayCodec,
): ReplayProfile {
    val videoBitrate = when (preset) {
        ReplayPreset.Original, ReplayPreset.Balanced -> 5_000_000
        ReplayPreset.Compact -> 3_000_000
    }
    return ReplayProfile(
        transcode = preset != ReplayPreset.Original,
        width = resolution.width,
        height = resolution.height,
        videoMime = codec.mime,
        videoBitrate = videoBitrate,
        iFrameIntervalSeconds = keyframes.seconds,
        audio = audio,
        audioBitrate = when (audio) {
            ReplayAudio.Stereo -> 96_000
            ReplayAudio.Mono -> 64_000
            ReplayAudio.Remove -> 0
        },
    )
}

/** Caption for the selected preset, used under the settings control. */
fun presetCaption(preset: ReplayPreset): String = when (preset) {
    ReplayPreset.Original ->
        "Stores the trimmed clip as-is. Largest files, no quality loss, slowest scrubbing."
    ReplayPreset.Balanced ->
        "1080p HEVC ~5 Mbps at 60 fps. Replays ~50% smaller with near-original detail and faster scrubbing."
    ReplayPreset.Compact ->
        "720p HEVC ~3 Mbps at 60 fps. Replays ~75% smaller, slightly softer, fastest scrubbing."
}

fun keyframesCaption(keyframes: ReplayKeyframes): String = when (keyframes) {
    ReplayKeyframes.Fast -> "Best scrub responsiveness; about 10% larger than standard."
    ReplayKeyframes.Standard -> "Balanced scrub speed and size."
    ReplayKeyframes.Sparse -> "Smallest files, but dragging feels steppier."
}

fun audioCaption(audio: ReplayAudio): String = when (audio) {
    ReplayAudio.Stereo -> "Keeps engine audio in stereo."
    ReplayAudio.Mono -> "About 5% smaller, centred audio."
    ReplayAudio.Remove -> "Smallest; replays have no audio."
}

fun resolutionCaption(resolution: ReplayResolution): String = when (resolution) {
    ReplayResolution.FullHd -> "Native detail; larger files."
    ReplayResolution.Hd -> "About 25% smaller than 1080p; slightly softer on large screens."
}

fun codecCaption(codec: ReplayCodec): String = when (codec) {
    ReplayCodec.Hevc -> "About half the size of H.264 at the same quality."
    ReplayCodec.H264 -> "Larger files, but plays on older devices without HEVC."
}
