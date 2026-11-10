package io.github.jlai403.pocketsimengineer.app.recording

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.view.Surface
import kotlin.concurrent.thread
import java.io.File

class ScreenRecorder(
    private val projection: MediaProjection,
    private val outputFile: File,
    private val config: VideoEncoderConfig = VideoEncoderConfig(),
    private val onError: (String) -> Unit = {},
) : Recorder {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var drainThread: Thread? = null
    private var trackIndex = -1
    private var muxerStarted = false

    @Volatile
    private var running = false

    override val isRecording: Boolean get() = running

    @Synchronized
    override fun start() {
        if (running) return
        outputFile.parentFile?.mkdirs()

        val format = MediaFormat.createVideoFormat(config.mime, config.width, config.height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(config.mime).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }
        codec = encoder
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        virtualDisplay = projection.createVirtualDisplay(
            "PocketSimEngineer",
            config.width,
            config.height,
            320,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null,
        )
        running = true
        drainThread = thread(name = "pocket-video-drain") { drainLoop() }
    }

    private fun drainLoop() {
        val encoder = codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            drain(encoder, bufferInfo)
        } catch (error: Exception) {
            running = false
            onError("Video encoder failed: ${error.message}")
        }
    }

    private fun drain(encoder: MediaCodec, bufferInfo: MediaCodec.BufferInfo) {
        while (running) {
            val index = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer?.addTrack(encoder.outputFormat) ?: -1
                        muxer?.start()
                        muxerStarted = trackIndex >= 0
                    }
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0 && muxerStarted) {
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer?.writeSampleData(trackIndex, buffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!running) return
        try {
            codec?.signalEndOfInputStream()
            drainThread?.join(2_000)
        } catch (_: Exception) {
            // Encoder may already be gone.
        }
        running = false
        drainThread = null

        virtualDisplay?.release()
        virtualDisplay = null
        inputSurface?.release()
        inputSurface = null
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
