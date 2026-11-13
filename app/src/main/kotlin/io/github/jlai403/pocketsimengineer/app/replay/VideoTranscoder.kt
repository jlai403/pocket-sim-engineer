package io.github.jlai403.pocketsimengineer.app.replay

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import io.github.jlai403.pocketsimengineer.app.settings.ReplayAudio
import io.github.jlai403.pocketsimengineer.app.settings.ReplayProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-encodes a trimmed lap clip with the user's replay profile. Runs on the main
 * looper because Media3 Transformer requires it; returns false on any failure so
 * the caller can keep the uncompressed clip.
 */
@OptIn(UnstableApi::class)
class VideoTranscoder(private val context: Context) {
    suspend fun transcode(source: File, output: File, profile: ReplayProfile): Boolean =
        withContext(Dispatchers.Main) {
            if (!profile.transcode || !source.isFile) return@withContext false
            output.parentFile?.mkdirs()
            output.delete()
            val result = CompletableDeferred<Boolean>()
            val audioProcessors = buildList {
                if (profile.audio == ReplayAudio.Mono) {
                    add(
                        ChannelMixingAudioProcessor().apply {
                            putChannelMixingMatrix(
                                ChannelMixingMatrix.create(probeAudioChannels(source), 1),
                            )
                        },
                    )
                }
            }
            val videoEffects = listOf(
                Presentation.createForWidthAndHeight(
                    profile.width,
                    profile.height,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                ),
            )
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(profile.videoBitrate)
                        .setiFrameIntervalSeconds(profile.iFrameIntervalSeconds)
                        .build(),
                )
                .apply {
                    if (profile.audio != ReplayAudio.Remove) {
                        setRequestedAudioEncoderSettings(
                            AudioEncoderSettings.Builder().setBitrate(profile.audioBitrate).build(),
                        )
                    }
                }
                .setEnableFallback(true)
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(profile.videoMime)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setRemoveAudio(profile.audio == ReplayAudio.Remove)
                .setEncoderFactory(encoderFactory)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            result.complete(output.isFile && output.length() > 0)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            result.complete(false)
                        }
                    },
                )
                .build()
            try {
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()
                transformer.start(item, output.absolutePath)
                val ok = result.await()
                if (!ok) output.delete()
                ok
            } catch (cancelled: CancellationException) {
                output.delete()
                throw cancelled
            } catch (_: Exception) {
                output.delete()
                false
            } finally {
                if (!result.isCompleted) transformer.cancel()
            }
        }

    private fun probeAudioChannels(file: File): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).firstNotNullOfOrNull { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    null
                }
            } ?: 1
        } catch (_: Exception) {
            1
        } finally {
            runCatching { extractor.release() }
        }
    }
}
