package io.github.jlai403.lapcraft.app.settings

import android.content.Context
import io.github.jlai403.lapcraft.core.network.Ipv4

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var ps5Ip: String
        get() = prefs.getString(KEY_IP, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_IP, value).apply()

    /** Saves a valid dotted-quad address and returns false without changing the stored value. */
    fun savePs5Ip(raw: String): Boolean {
        val normalized = Ipv4.normalize(raw) ?: return false
        ps5Ip = normalized
        return true
    }

    var imperialUnits: Boolean
        get() = prefs.getBoolean(KEY_IMPERIAL, false)
        set(value) = prefs.edit().putBoolean(KEY_IMPERIAL, value).apply()

    /** Haptic ticks while dragging the frame ruler. */
    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /** Playback rate used when a replay opens; changes on the lap screen last one session. */
    var defaultPlaybackSpeed: Float
        get() = prefs.getFloat(KEY_PLAYBACK_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()

    /** Show speed, gear, and pedal sparklines over the replay. */
    var telemetryHud: Boolean
        get() = prefs.getBoolean(KEY_TELEMETRY_HUD, true)
        set(value) = prefs.edit().putBoolean(KEY_TELEMETRY_HUD, value).apply()

    /** Time shown either side of the cursor on the telemetry charts, in seconds. */
    var chartWindowSeconds: Int
        get() = prefs.getInt(KEY_CHART_WINDOW, 5).coerceIn(2, 10)
        set(value) = prefs.edit().putInt(KEY_CHART_WINDOW, value.coerceIn(2, 10)).apply()

    /** How new replay clips are compressed; defaults to the balanced profile. */
    var replayPreset: ReplayPreset
        get() = enumPref(KEY_REPLAY_PRESET, ReplayPreset.Balanced)
        set(value) = prefs.edit().putString(KEY_REPLAY_PRESET, value.name).apply()

    var replayKeyframes: ReplayKeyframes
        get() = enumPref(KEY_REPLAY_KEYFRAMES, ReplayKeyframes.Frame)
        set(value) = prefs.edit().putString(KEY_REPLAY_KEYFRAMES, value.name).apply()

    var replayAudio: ReplayAudio
        get() = enumPref(KEY_REPLAY_AUDIO, ReplayAudio.Stereo)
        set(value) = prefs.edit().putString(KEY_REPLAY_AUDIO, value.name).apply()

    var replayResolution: ReplayResolution
        get() = enumPref(KEY_REPLAY_RESOLUTION, ReplayResolution.FullHd)
        set(value) = prefs.edit().putString(KEY_REPLAY_RESOLUTION, value.name).apply()

    var replayCodec: ReplayCodec
        get() = enumPref(KEY_REPLAY_CODEC, ReplayCodec.Hevc)
        set(value) = prefs.edit().putString(KEY_REPLAY_CODEC, value.name).apply()

    /** The resolved encoder profile for new imports. */
    fun replayProfile(): ReplayProfile = replayProfile(
        preset = replayPreset,
        keyframes = replayKeyframes,
        audio = replayAudio,
        resolution = replayResolution,
        codec = replayCodec,
    )

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, null).orEmpty()) }.getOrDefault(default)

    /** Developer preview: show the empty library while laps stay on disk. */
    var previewEmptyLibrary: Boolean
        get() = prefs.getBoolean(KEY_PREVIEW_EMPTY, false)
        set(value) = prefs.edit().putBoolean(KEY_PREVIEW_EMPTY, value).apply()

    /** Developer tools: exposes maintenance actions such as re-encoding a clip. */
    var developerTools: Boolean
        get() = prefs.getBoolean(KEY_DEVELOPER_TOOLS, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVELOPER_TOOLS, value).apply()

    /** Last track-identification backfill this install completed. */
    var trackIdentificationVersion: Int
        get() = prefs.getInt(KEY_TRACK_ID_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_TRACK_ID_VERSION, value).apply()

    private companion object {
        // The pre-existing preferences file; changing it would drop the saved PS5 address.
        const val PREFS = "settings"
        const val KEY_IP = "ps5_ip"
        const val KEY_IMPERIAL = "imperial_units"
        const val KEY_HAPTICS = "frame_ruler_haptics"
        const val KEY_PLAYBACK_SPEED = "default_playback_speed"
        const val KEY_TELEMETRY_HUD = "telemetry_hud"
    const val KEY_CHART_WINDOW = "chart_window_seconds"
        const val KEY_REPLAY_PRESET = "replay_preset"
        const val KEY_REPLAY_KEYFRAMES = "replay_keyframes"
        const val KEY_REPLAY_AUDIO = "replay_audio"
        const val KEY_REPLAY_RESOLUTION = "replay_resolution"
        const val KEY_REPLAY_CODEC = "replay_codec"
        const val KEY_PREVIEW_EMPTY = "preview_empty_library"
        const val KEY_DEVELOPER_TOOLS = "developer_tools"
        const val KEY_TRACK_ID_VERSION = "track_id_version"
    }
}
