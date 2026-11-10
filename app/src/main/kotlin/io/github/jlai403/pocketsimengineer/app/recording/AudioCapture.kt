package io.github.jlai403.pocketsimengineer.app.recording

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import kotlin.concurrent.thread
import kotlin.math.abs
import java.io.File

class AudioCapture(
    private val projection: MediaProjection,
    private val outputFile: File,
    private val config: AudioEncoderConfig = AudioEncoderConfig(),
    private val onError: (String) -> Unit = {},
) : Recorder {
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var drainThread: Thread? = null
    private var trackIndex = -1
    private var muxerStarted = false

    @Volatile
    private var running = false

    @Volatile
    var silent = true
        private set

    @Volatile
    var frames = 0L
        private set

    override val isRecording: Boolean get() = running

    @Synchronized
    override fun start() {
        if (running) return
        outputFile.parentFile?.mkdirs()

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(config.sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(maxOf(minBuffer, 16_384))
            .build()

        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" }
        audioRecord = record

        val format = MediaFormat.createAudioFormat(config.mime, config.sampleRate, config.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val encoder = MediaCodec.createEncoderByType(config.mime).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        codec = encoder
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        running = true
        record.startRecording()
        drainThread = thread(name = "pocket-audio-drain") { drainLoop(record, encoder) }
    }

    private fun drainLoop(record: AudioRecord, encoder: MediaCodec) {
        try {
            drain(record, encoder)
        } catch (error: Exception) {
            running = false
            onError("Audio capture failed: ${error.message}")
        }
    }

    private fun drain(record: AudioRecord, encoder: MediaCodec) {
        val pcm = ByteArray(4_096)
        val info = MediaCodec.BufferInfo()

        while (running) {
            val inputIndex = encoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val input = encoder.getInputBuffer(inputIndex)
                if (input != null) {
                    input.clear()
                    val read = record.read(pcm, 0, minOf(pcm.size, input.remaining()))
                    if (read > 0) {
                        frames++
                        input.put(pcm, 0, read)
                        encoder.queueInputBuffer(
                            inputIndex,
                            0,
                            read,
                            System.nanoTime() / 1_000,
                            0,
                        )
                        observeAmplitude(pcm, read)
                    } else {
                        encoder.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                    }
                }
            }

            val outputIndex = encoder.dequeueOutputBuffer(info, 0)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer?.addTrack(encoder.outputFormat) ?: -1
                        muxer?.start()
                        muxerStarted = trackIndex >= 0
                    }
                }
                outputIndex >= 0 -> {
                    val output = encoder.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0 && muxerStarted) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        muxer?.writeSampleData(trackIndex, output, info)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun observeAmplitude(pcm: ByteArray, count: Int) {
        var max = 0
        var i = 0
        while (i + 1 < count) {
            val sample = ((pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
            val magnitude = abs(sample)
            if (magnitude > max) max = magnitude
            i += 2
        }
        if (max > 100) silent = false
    }

    @Synchronized
    override fun stop() {
        if (!running) return
        running = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        drainThread?.join(2_000)
        drainThread = null
        audioRecord?.release()
        audioRecord = null
        codec?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        codec = null
        if (muxerStarted) {
            try {
                muxer?.stop()
            } catch (_: Exception) {
            }
        }
        muxer?.release()
        muxer = null
        muxerStarted = false
    }
}
