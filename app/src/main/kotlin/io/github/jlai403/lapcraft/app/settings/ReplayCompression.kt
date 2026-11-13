package io.github.jlai403.lapcraft.app.settings

import androidx.media3.common.MimeTypes

/** How new replay clips are stored. Defaults are the balanced, quality-first profile. */
enum class ReplayPreset(val label: String) {
    Original("Original"),
    Balanced("Balanced"),
    Compact("Compact"),
}

enum class ReplayKeyframes(val label: String, val seconds: Float) {
    Frame("Frame (0.1 s)", 0.1f),
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

/** Keyframe intervals at or below this pay the short-GOP size premium. */
private const val SHORT_GOP_SECONDS = 0.2f

/** Percent of the baseline estimate a short-GOP clip needs for its extra keyframes. */
private const val SHORT_GOP_PERCENT = 135L

fun keyframesCaption(keyframes: ReplayKeyframes): String = when (keyframes) {
    ReplayKeyframes.Frame -> "Smoothest scrubbing: an exact seek decodes at most six frames. About 35% larger than Fast."
    ReplayKeyframes.Fast -> "About 25% smaller than Frame; an exact seek may decode up to 30 frames."
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

/** Rough stored size for a trimmed clip; encoders vary with scene complexity. */
fun estimateReplayBytes(profile: ReplayProfile, durationMs: Long): Long {
    if (!profile.transcode || durationMs <= 0) return 0
    val audioBits = if (profile.audio == ReplayAudio.Remove) 0 else profile.audioBitrate
    val bytes = (profile.videoBitrate.toLong() + audioBits) * durationMs / 8_000L
    // Short GOPs spend extra bits on keyframes, which the bitrate alone does not show.
    return if (profile.iFrameIntervalSeconds <= SHORT_GOP_SECONDS) {
        bytes * SHORT_GOP_PERCENT / 100
    } else {
        bytes
    }
}

/** Estimates the trimmed original from the source size and the trim fraction. */
fun estimatedOriginalBytes(sourceBytes: Long, trimmedMs: Long, sourceDurationMs: Long): Long {
    if (sourceBytes <= 0 || sourceDurationMs <= 0 || trimmedMs <= 0) return 0
    return sourceBytes * trimmedMs / sourceDurationMs
}
