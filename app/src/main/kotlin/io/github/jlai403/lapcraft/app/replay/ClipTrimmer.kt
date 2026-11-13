package io.github.jlai403.lapcraft.app.replay

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.jlai403.lapcraft.core.clip.TrimRange
import java.io.File
import java.nio.ByteBuffer

data class TrimResult(val output: File, val startOffsetMs: Int)

/** Cuts a replay to the lap range by copying samples, starting at the preceding video keyframe. */
class ClipTrimmer {
    fun trim(source: File, output: File, range: TrimRange): TrimResult? {
        if (!source.isFile || range.endMs <= range.startMs) return null

        val extractor = MediaExtractor()
        val temp = File(output.absolutePath + ".tmp")
        var muxer: MediaMuxer? = null
        var started = false
        var result: TrimResult? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var videoIndex = -1
            var audioIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoIndex < 0) {
                    videoIndex = index
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioIndex < 0) {
                    audioIndex = index
                    audioFormat = format
                }
            }
            if (videoIndex < 0 || videoFormat == null) return null
            val video = videoFormat
            val audio = audioFormat

            extractor.selectTrack(videoIndex)
            if (audioIndex >= 0) extractor.selectTrack(audioIndex)

            val startUs = range.startMs * 1_000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)

            var keyframeUs: Long? = null
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (extractor.sampleTrackIndex == videoIndex) {
                    keyframeUs = extractor.sampleTime
                    break
                }
                extractor.advance()
            }
            val keyframe = keyframeUs ?: return null
            extractor.seekTo(keyframe, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            output.parentFile?.mkdirs()
            temp.delete()
            val mux = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val muxVideo = mux.addTrack(video)
            val muxAudio = audio?.let { mux.addTrack(it) } ?: -1
            mux.start()
            started = true

            val endUs = range.endMs * 1_000
            val info = MediaCodec.BufferInfo()
            var samples = 0
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time > endUs) break
                if (time >= keyframe) {
                    val track = extractor.sampleTrackIndex
                    val muxTrack = if (track == videoIndex) muxVideo else muxAudio
                    if (muxTrack >= 0) {
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        info.set(0, size, time - keyframe, flags)
                        mux.writeSampleData(muxTrack, buffer, info)
                        samples++
                    }
                }
                extractor.advance()
            }

            if (samples <= 0) return null
            mux.stop()
            started = false
            if (temp.length() <= 0L) return null
            if (!swap(temp, output)) return null
            result = TrimResult(output, ((startUs - keyframe) / 1_000).toInt())
        } catch (_: Exception) {
            result = null
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            if (result == null) temp.delete()
        }
        return result
    }

    /** Replaces an existing clip without losing it if the rename fails. */
    private fun swap(temp: File, output: File): Boolean {
        val backup = File(output.absolutePath + ".bak")
        backup.delete()
        val hadOutput = output.exists()
        if (hadOutput && !output.renameTo(backup)) return false
        if (temp.renameTo(output)) {
            backup.delete()
            return true
        }
        if (hadOutput) backup.renameTo(output)
        return false
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
    }
}
