package io.github.jlai403.lapcraft.app.replay

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import io.github.jlai403.lapcraft.app.settings.ReplayAudio
import io.github.jlai403.lapcraft.app.settings.ReplayKeyframes
import io.github.jlai403.lapcraft.app.settings.ReplayProfile
import io.github.jlai403.lapcraft.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "LapReencode"

/**
 * Re-encodes one lap clip in place with a short GOP. Resolution, codec and bitrate
 * follow the source; the old clip, its temporaries and its scrub cache are removed
 * only after the new file has replaced it.
 */
@OptIn(UnstableApi::class)
class LapReencoder(
    private val store: SessionStore,
    private val transcoder: VideoTranscoder,
) {
    suspend fun reencode(sessionId: String, lapIndex: Int): Boolean {
        val lap = withContext(Dispatchers.IO) { store.lap(sessionId, lapIndex) } ?: return false
        if (lap.videoFile.isBlank()) return false
        val dir = store.sessionDir(sessionId)
        val clip = File(dir, lap.videoFile).takeIf { it.isFile } ?: return false
        val durationMs = withContext(Dispatchers.IO) { probeClip(clip).durationMs }
        val profile = withContext(Dispatchers.IO) { scrubProfile(clip, durationMs) }
        val output = File(clip.absolutePath + ".reencode.mp4")
        output.delete()
        val started = SystemClock.elapsedRealtime()
        val transcoded = transcoder.transcode(clip, output, profile)
        if (!transcoded || !output.isFile || output.length() == 0L) {
            output.delete()
            Log.w(TAG, "re-encode failed for ${lap.videoFile}")
            return false
        }
        Log.i(
            TAG,
            "re-encoded ${lap.videoFile}: ${syncSamples(output)} keyframes, " +
                "${output.length()} bytes in ${SystemClock.elapsedRealtime() - started} ms",
        )
        return withContext(Dispatchers.IO) {
            val swapped = swap(clip, output)
            if (swapped) store.deleteScrubArtifacts(dir, lap.videoFile)
            swapped
        }
    }

    private fun scrubProfile(clip: File, durationMs: Long): ReplayProfile {
        val extractor = MediaExtractor()
        var width = 1920
        var height = 1080
        var mime = MimeTypes.VIDEO_H265
        var bitrate = 0
        var hasAudio = false
        try {
            extractor.setDataSource(clip.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    trackMime.startsWith("video/") -> {
                        width = runCatching { format.getInteger(MediaFormat.KEY_WIDTH) }
                            .getOrDefault(width)
                        height = runCatching { format.getInteger(MediaFormat.KEY_HEIGHT) }
                            .getOrDefault(height)
                        mime = trackMime
                        bitrate = runCatching { format.getInteger(MediaFormat.KEY_BIT_RATE) }
                            .getOrDefault(0)
                            .takeIf { it > 0 } ?: 0
                    }
                    trackMime.startsWith("audio/") -> hasAudio = true
                }
            }
        } catch (_: Exception) {
            // Keep the defaults; the transcoder reports a failure and the clip is unchanged.
        } finally {
            runCatching { extractor.release() }
        }
        val sourceBitrate = bitrate.takeIf { it > 0 }
            ?: durationMs.takeIf { it > 0 }?.let { (clip.length() * 8_000 / it).toInt() }
            ?: 5_000_000
        return ReplayProfile(
            transcode = true,
            width = width,
            height = height,
            videoMime = mime,
            // Keyframes every 0.1 s cost more bits than the clip they replace.
            videoBitrate = (sourceBitrate * 1.35f).roundToInt(),
            iFrameIntervalSeconds = ReplayKeyframes.Frame.seconds,
            audio = if (hasAudio) ReplayAudio.Stereo else ReplayAudio.Remove,
            audioBitrate = 96_000,
        )
    }

    /** Counts sync samples, which is the clip's keyframe count. */
    private fun syncSamples(file: File): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: return 0
            extractor.selectTrack(track)
            var count = 0
            while (extractor.sampleTime >= 0) {
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) count++
                if (!extractor.advance()) break
            }
            count
        } catch (_: Exception) {
            0
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun swap(clip: File, output: File): Boolean {
        val backup = File(clip.absolutePath + ".bak")
        backup.delete()
        if (!clip.renameTo(backup)) {
            output.delete()
            return false
        }
        if (output.renameTo(clip)) {
            backup.delete()
            return true
        }
        backup.renameTo(clip)
        output.delete()
        return false
    }
}
