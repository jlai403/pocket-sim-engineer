package io.github.jlai403.pocketsimengineer.app.settings

import android.content.Context
import io.github.jlai403.pocketsimengineer.app.recording.RecordingService
import io.github.jlai403.pocketsimengineer.core.network.Ipv4

enum class VideoQuality(val width: Int, val height: Int) {
    P1080(1920, 1080),
    P720(1280, 720);

    companion object {
        fun fromName(name: String?): VideoQuality =
            entries.firstOrNull { it.name == name } ?: P1080
    }
}

enum class VideoCodec(val mime: String) {
    H264("video/avc"),
    HEVC("video/hevc");

    companion object {
        fun fromMime(mime: String?): VideoCodec =
            entries.firstOrNull { it.mime == mime } ?: H264
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(RecordingService.SETTINGS, Context.MODE_PRIVATE)

    var ps5Ip: String
        get() = prefs.getString(KEY_IP, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_IP, value).apply()

    /** Saves a valid dotted-quad address and returns false without changing the stored value. */
    fun savePs5Ip(raw: String): Boolean {
        val normalized = Ipv4.normalize(raw) ?: return false
        ps5Ip = normalized
        return true
    }

    var keepRaw: Boolean
        get() = prefs.getBoolean(RecordingService.KEEP_RAW, false)
        set(value) = prefs.edit().putBoolean(RecordingService.KEEP_RAW, value).apply()

    var quality: VideoQuality
        get() = VideoQuality.fromName(prefs.getString(KEY_QUALITY, null))
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    var codec: VideoCodec
        get() = VideoCodec.fromMime(prefs.getString(KEY_CODEC, null))
        set(value) = prefs.edit().putString(KEY_CODEC, value.mime).apply()

    var bitrateMbps: Int
        get() = prefs.getInt(KEY_BITRATE_MBPS, 10)
        set(value) = prefs.edit().putInt(KEY_BITRATE_MBPS, value).apply()

    var imperialUnits: Boolean
        get() = prefs.getBoolean(KEY_IMPERIAL, false)
        set(value) = prefs.edit().putBoolean(KEY_IMPERIAL, value).apply()

    private companion object {
        const val KEY_IP = "ps5_ip"
        const val KEY_QUALITY = "video_quality"
        const val KEY_CODEC = "video_codec"
        const val KEY_BITRATE_MBPS = "video_bitrate_mbps"
        const val KEY_IMPERIAL = "imperial_units"
    }
}
