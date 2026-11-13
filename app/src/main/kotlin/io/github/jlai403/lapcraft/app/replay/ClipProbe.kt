package io.github.jlai403.lapcraft.app.replay

import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.roundToLong

data class ClipProbe(val durationMs: Long, val frameDurationUs: Long) {
    /** Visual ruler spacing; stepping uses the exact [frameDurationUs]. */
    val frameMs: Long get() = (frameDurationUs / 1_000).coerceAtLeast(1L)
}

/** Reads the clip duration and the presentation length of one video frame. */
fun probeClip(file: File): ClipProbe {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val duration = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val captureFps = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
        val frames = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            ?.toLongOrNull()
        val frameUs = when {
            captureFps != null && captureFps > 0f -> (1_000_000f / captureFps).roundToLong()
            frames != null && frames > 0 && duration > 0 -> duration * 1_000 / frames
            else -> 33_000L
        }.coerceAtLeast(1L)
        ClipProbe(duration, frameUs)
    } catch (_: Exception) {
        ClipProbe(0L, 33_000L)
    } finally {
        runCatching { retriever.release() }
    }
}
