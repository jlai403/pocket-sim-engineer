package io.github.jlai403.pocketsimengineer.app.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.RemuxPlanner
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts per-lap clips from the finalized raw recording and copies video and audio without
 * re-encoding. Telemetry and encoder timestamps share the `System.nanoTime()` domain, so a
 * lap's nanosecond bounds map directly onto the extractor's microsecond PTS.
 */
class LapRemuxer(private val sessionDir: File) {

    data class RemuxResult(
        val laps: List<LapMetadata>,
        val allSucceeded: Boolean,
    )

    fun remux(laps: List<LapMetadata>, rawVideo: File, audio: File?): RemuxResult {
        if (laps.isEmpty() || !rawVideo.isFile) {
            return RemuxResult(laps, allSucceeded = false)
        }
        val videoTrack = probe(rawVideo, "video/") ?: return RemuxResult(laps, false)
        val audioTrack = audio?.takeIf { it.isFile }?.let { probe(it, "audio/") }
        val baseUs = minOf(videoTrack.firstPtsUs, audioTrack?.firstPtsUs ?: videoTrack.firstPtsUs)
        val durationUs = videoTrack.durationUs

        val output = ArrayList<LapMetadata>(laps.size)
        var allSucceeded = true
        for (lap in laps) {
            val clipped = runCatching {
                cutLap(lap, rawVideo, videoTrack, audio, audioTrack, baseUs, durationUs)
            }.getOrNull()
            if (clipped == null) {
                allSucceeded = false
                output += lap.copy(needsCut = true)
            } else {
                output += clipped
            }
        }
        return RemuxResult(output, allSucceeded)
    }

    private fun cutLap(
        lap: LapMetadata,
        rawVideo: File,
        videoTrack: Track,
        audio: File?,
        audioTrack: Track?,
        baseUs: Long,
        durationUs: Long,
    ): LapMetadata? {
        val lapStartUs = lap.startNanos / 1_000 - baseUs
        val lapEndUs = lap.endNanos / 1_000 - baseUs
        val requested = RemuxPlanner.requestedRange(lapStartUs, lapEndUs, durationUs) ?: return null

        val video = MediaExtractor()
        var audioExtractor: MediaExtractor? = null
        val outputFile = File(sessionDir, "laps/lap-%03d.mp4".format(lap.index))
        outputFile.parentFile?.mkdirs()
        var muxer: MediaMuxer? = null
        var started = false
        var success = false
        try {
            video.setDataSource(rawVideo.absolutePath)
            video.selectTrack(videoTrack.index)
            video.seekTo(requested.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val keyframeUs = nextSampleTime(video) ?: return null
            val aligned = RemuxPlanner.alignToKeyframe(requested.startUs, keyframeUs, lapStartUs)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideo = muxer.addTrack(videoTrack.format)
            var muxAudio = -1

            val audioSource = audio?.takeIf { it.isFile }
            if (audioSource != null && audioTrack != null) {
                audioExtractor = MediaExtractor().apply {
                    setDataSource(audioSource.absolutePath)
                    selectTrack(audioTrack.index)
                }
                muxAudio = muxer.addTrack(audioTrack.format)
            }
            muxer.start()
            started = true

            copyVideo(video, muxer, muxVideo, aligned.clipStartUs, requested.endUs)
            if (audioExtractor != null && muxAudio >= 0) {
                audioExtractor.seekTo(aligned.clipStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                copyAudio(audioExtractor, muxer, muxAudio, aligned.clipStartUs, requested.endUs)
            }

            success = true
            return lap.copy(
                videoFile = "laps/lap-%03d.mp4".format(lap.index),
                videoStartOffsetMs = aligned.videoStartOffsetMs,
                needsCut = false,
            )
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { audioExtractor?.release() }
            runCatching { video.release() }
            if (!success) outputFile.delete()
        }
    }

    private fun copyVideo(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        clipStartUs: Long,
        clipEndUs: Long,
    ) {
        val info = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val timeUs = extractor.sampleTime
            if (timeUs > clipEndUs) break
            if (timeUs >= clipStartUs) {
                info.set(0, size, timeUs - clipStartUs, extractor.sampleFlags)
                muxer.writeSampleData(track, buffer, info)
            }
            extractor.advance()
        }
    }

    private fun copyAudio(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        clipStartUs: Long,
        clipEndUs: Long,
    ) {
        val info = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val timeUs = extractor.sampleTime
            if (timeUs > clipEndUs) break
            if (timeUs >= clipStartUs) {
                info.set(0, size, timeUs - clipStartUs, extractor.sampleFlags)
                muxer.writeSampleData(track, buffer, info)
            }
            extractor.advance()
        }
    }

    private fun nextSampleTime(extractor: MediaExtractor): Long? {
        val probe = ByteBuffer.allocateDirect(BUFFER_SIZE)
        val size = extractor.readSampleData(probe, 0)
        return if (size < 0) null else extractor.sampleTime
    }

    private class Track(
        val index: Int,
        val format: MediaFormat,
        val firstPtsUs: Long,
        val durationUs: Long,
    )

    private fun probe(file: File, mimePrefix: String): Track? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith(mimePrefix)) continue
                extractor.selectTrack(index)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val first = nextSampleTime(extractor) ?: return null
                val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    lastSampleTime(extractor) ?: first
                }
                return Track(index, format, first, duration)
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun lastSampleTime(extractor: MediaExtractor): Long? {
        extractor.seekTo(Long.MAX_VALUE, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var last: Long? = null
        val probe = ByteBuffer.allocateDirect(BUFFER_SIZE)
        while (true) {
            val size = extractor.readSampleData(probe, 0)
            if (size < 0) break
            last = extractor.sampleTime
            extractor.advance()
        }
        return last
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
    }
}
