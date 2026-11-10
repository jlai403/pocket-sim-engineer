package io.github.jlai403.pocketsimengineer.app.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import io.github.jlai403.pocketsimengineer.core.session.RemuxPlanner
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Cuts per-lap clips from the finalized raw recording, copying video and audio without
 * re-encoding. Lap bounds are in the `System.nanoTime()` domain; each extractor's timestamps are
 * mapped through the first sample so the cut also works if the muxer rebased them.
 */
class LapRemuxer(private val sessionDir: File) : LapCutter {

    override fun cut(
        laps: List<LapMetadata>,
        rawVideo: File,
        audio: File?,
        recordingStartNanos: Long,
    ): RemuxOutcome {
        if (laps.isEmpty() || !rawVideo.isFile) return RemuxOutcome(laps, allSucceeded = false)
        val videoTrack = probe(rawVideo, "video/")
            ?: return RemuxOutcome(laps.map { it.copy(needsCut = true) }, false)

        val output = ArrayList<LapMetadata>(laps.size)
        var allSucceeded = true
        for (lap in laps) {
            val clipped = runCatching {
                cutLap(lap, rawVideo, videoTrack, audio, recordingStartNanos)
            }.getOrNull()
            if (clipped == null) {
                allSucceeded = false
                output += lap.copy(needsCut = true)
            } else {
                output += clipped
            }
        }
        return RemuxOutcome(output, allSucceeded)
    }

    private fun cutLap(
        lap: LapMetadata,
        rawVideo: File,
        videoTrack: Track,
        audio: File?,
        recordingStartNanos: Long,
    ): LapMetadata? {
        val videoLapStartUs = toFileUs(lap.startNanos, recordingStartNanos, videoTrack.firstPtsUs)
        val videoLapEndUs = toFileUs(lap.endNanos, recordingStartNanos, videoTrack.firstPtsUs)
        val requested = RemuxPlanner.requestedRange(
            videoLapStartUs,
            videoLapEndUs,
            videoTrack.firstPtsUs + videoTrack.durationUs,
        ) ?: return null

        val video = MediaExtractor()
        var audioExtractor: MediaExtractor? = null
        val outputFile = File(sessionDir, "laps/lap-%03d.mp4".format(lap.index))
        val tempFile = File(sessionDir, "laps/lap-%03d.mp4.tmp".format(lap.index))
        outputFile.parentFile?.mkdirs()
        var muxer: MediaMuxer? = null
        var started = false
        var success = false
        try {
            video.setDataSource(rawVideo.absolutePath)
            video.selectTrack(videoTrack.index)
            video.seekTo(requested.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val keyframeUs = nextSampleTime(video) ?: return null
            val aligned = RemuxPlanner.alignToKeyframe(requested.startUs, keyframeUs, videoLapStartUs)

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideo = muxer.addTrack(videoTrack.format)
            var muxAudio = -1

            val audioSource = audio?.takeIf { it.isFile && it.length() > 0 }
            var audioTrack: Track? = null
            if (audioSource != null) {
                audioTrack = probe(audioSource, "audio/") ?: return null
                audioExtractor = MediaExtractor().apply {
                    setDataSource(audioSource.absolutePath)
                    selectTrack(audioTrack.index)
                }
                muxAudio = muxer.addTrack(audioTrack.format)
            }
            muxer.start()
            started = true

            val videoSamples = copyVideo(video, muxer, muxVideo, aligned.clipStartUs, requested.endUs)
            if (videoSamples <= 0) return null

            if (audioExtractor != null && audioTrack != null && muxAudio >= 0) {
                val clipStartNanos = lap.startNanos - aligned.videoStartOffsetMs * 1_000_000L
                val audioClipStartUs = toFileUs(
                    clipStartNanos,
                    recordingStartNanos,
                    audioTrack.firstPtsUs,
                )
                val audioClipEndUs = toFileUs(
                    lap.endNanos + RemuxPlanner.POST_ROLL_US * 1_000,
                    recordingStartNanos,
                    audioTrack.firstPtsUs,
                )
                audioExtractor.seekTo(audioClipStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                copyAudio(audioExtractor, muxer, muxAudio, audioClipStartUs, audioClipEndUs)
            }

            muxer.stop()
            started = false
            check(tempFile.isFile && tempFile.length() > 0) { "empty remux output" }
            outputFile.delete()
            check(tempFile.renameTo(outputFile)) { "could not rename remux output" }
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
            tempFile.delete()
            if (!success && outputFile.isFile && outputFile.length() == 0L) outputFile.delete()
        }
    }

    private fun copyVideo(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        clipStartUs: Long,
        clipEndUs: Long,
    ): Int {
        val info = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        var samples = 0
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val timeUs = extractor.sampleTime
            if (timeUs > clipEndUs) break
            if (timeUs >= clipStartUs) {
                info.set(0, size, timeUs - clipStartUs, extractor.sampleFlags)
                muxer.writeSampleData(track, buffer, info)
                samples++
            }
            extractor.advance()
        }
        return samples
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

    /**
     * Maps a `System.nanoTime()` value into the file timestamp domain. If the file's first sample
     * still looks like the absolute monotonic clock, use it directly; otherwise anchor on the
     * first sample and the recording start.
     */
    private fun toFileUs(nanos: Long, recordingStartNanos: Long, firstPtsUs: Long): Long {
        if (recordingStartNanos <= 0) return nanos / 1_000
        val expected = recordingStartNanos / 1_000
        return if (abs(firstPtsUs - expected) <= CLOCK_MATCH_WINDOW_US) {
            nanos / 1_000
        } else {
            firstPtsUs + (nanos - recordingStartNanos) / 1_000
        }
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
                    ((lastSampleTime(extractor) ?: first) - first).coerceAtLeast(0)
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
        const val CLOCK_MATCH_WINDOW_US = 60_000_000L
    }
}
