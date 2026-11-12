package io.github.jlai403.pocketsimengineer.app.replay

import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.roundToLong

data class ClipProbe(val durationMs: Long, val frameMs: Long)

/** Reads the clip duration and the length of one video frame. */
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
        val frameMs = when {
            captureFps != null && captureFps > 0f -> (1_000f / captureFps).roundToLong()
            frames != null && frames > 0 && duration > 0 -> duration / frames
            else -> 33L
        }.coerceAtLeast(1L)
        ClipProbe(duration, frameMs)
    } catch (_: Exception) {
        ClipProbe(0L, 33L)
    } finally {
        runCatching { retriever.release() }
    }
}
