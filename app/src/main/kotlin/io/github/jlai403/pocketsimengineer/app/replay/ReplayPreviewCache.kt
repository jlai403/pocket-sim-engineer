package io.github.jlai403.pocketsimengineer.app.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val INDEX_VERSION = 2
// Matches the lap player's video box on a 1080 px display, so the preview is 1:1.
private const val PREVIEW_WIDTH = 960
private const val JPEG_QUALITY = 70
private const val BITMAP_CACHE_BYTES = 24 * 1024 * 1024
private const val TAG = "ReplayPreview"

/** One cached preview frame per video keyframe, plus the keyframe clock. */
@Serializable
internal data class PreviewIndex(
    val version: Int = INDEX_VERSION,
    val width: Int,
    val height: Int,
    val timesMs: List<Long>,
    val sourceBytes: Long = 0,
    val sourceModifiedMs: Long = 0,
)

internal data class ReplayPreview(
    val directory: File,
    val index: PreviewIndex,
    val cacheKey: String,
)

/** The cached keyframe at or before [timeMs]; -1 when there is none or the time is negative. */
internal fun nearestPreviewFrame(timesMs: List<Long>, timeMs: Long): Int {
    if (timesMs.isEmpty() || timeMs < 0) return -1
    val exact = timesMs.binarySearch(timeMs)
    if (exact >= 0) return exact
    return (-exact - 2).coerceIn(0, timesMs.lastIndex)
}

/**
 * Builds and reads the scrub preview strip for a trimmed lap clip. Generation
 * decodes only sync samples, writes small JPEGs and an index, then swaps the
 * directory in atomically. A missing or broken cache returns null so callers
 * fall back to keyframe seeks.
 */
internal class ReplayPreviewCache(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Deferred<ReplayPreview?>>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val bitmaps = object : LruCache<String, Bitmap>(BITMAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun warm(video: File) {
        if (!video.isFile) return
        val deferred = job(video)
        deferred.start()
    }

    suspend fun ensure(video: File): ReplayPreview? {
        if (!video.isFile) return null
        val key = cacheKey(video)
        Log.i(TAG, "ensure ${video.name} key=$key")
        val deferred = job(video)
        deferred.start()
        val result = try {
            deferred.await()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (result == null) jobs.remove(key)
        return result
    }

    private fun job(video: File): Deferred<ReplayPreview?> {
        val key = cacheKey(video)
        return jobs.computeIfAbsent(key) {
            scope.async(start = CoroutineStart.LAZY) {
                loadOrBuild(video, key).also {
                    if (it == null) {
                        jobs.remove(key)
                        Log.w(TAG, "no preview for ${video.name}")
                    } else {
                        Log.i(TAG, "preview ready for ${video.name}: ${it.index.timesMs.size} frames")
                    }
                }
            }
        }
    }

    suspend fun frame(preview: ReplayPreview, index: Int): Bitmap? {
        val key = preview.cacheKey + "#" + index
        bitmaps.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            bitmaps.get(key) ?: run {
                val file = File(preview.directory, "%05d.jpg".format(index))
                BitmapFactory.decodeFile(file.absolutePath)?.also { bitmaps.put(key, it) }
                    ?: run {
                        Log.w(TAG, "cannot decode ${file.name} (${file.length()} bytes)")
                        null
                    }
            }
        }
    }

    private fun loadOrBuild(video: File, cacheKey: String): ReplayPreview? {
        val directory = previewDirectory(video)
        readIndex(directory, video)?.let { return ReplayPreview(directory, it, cacheKey) }
        directory.deleteRecursively()
        tempDirectory(video).deleteRecursively()
        return build(video, directory, cacheKey)
    }

    private fun readIndex(directory: File, video: File): PreviewIndex? {
        val file = File(directory, "index.json")
        if (!file.isFile) return null
        val index = runCatching { json.decodeFromString<PreviewIndex>(file.readText()) }.getOrNull()
        if (index == null) {
            Log.w(TAG, "unreadable index in ${directory.name}")
            return null
        }
        val valid = index.version == INDEX_VERSION &&
            index.timesMs.isNotEmpty() &&
            index.sourceBytes == video.length() &&
            index.sourceModifiedMs == video.lastModified() &&
            File(directory, "00000.jpg").isFile
        if (!valid) {
            Log.w(
                TAG,
                "stale index ${directory.name}: version=${index.version} " +
                    "bytes=${index.sourceBytes}/${video.length()} " +
                    "mtime=${index.sourceModifiedMs}/${video.lastModified()} " +
                    "frames=${index.timesMs.size}",
            )
        }
        return index.takeIf { valid }
    }

    private fun build(video: File, directory: File, cacheKey: String): ReplayPreview? {
        if (!video.isFile) return null
        val started = SystemClock.elapsedRealtime()
        val timesUs = keyframeTimes(video)
        if (timesUs.isEmpty()) {
            Log.w(TAG, "no video keyframes in ${video.name}")
            return null
        }
        Log.i(TAG, "building ${video.name}: ${timesUs.size} keyframes")
        val temp = tempDirectory(video)
        if (!temp.mkdirs()) {
            Log.w(TAG, "cannot create ${temp.absolutePath}")
            return null
        }
        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var skipped = 0
        val savedTimes = mutableListOf<Long>()
        return try {
            retriever.setDataSource(video.absolutePath)
            val sourceWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return null
            val sourceHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return null
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val rotated = rotation == 90 || rotation == 270
            val landscapeWidth = if (rotated) sourceHeight else sourceWidth
            val landscapeHeight = if (rotated) sourceWidth else sourceHeight
            val previewHeight = (landscapeHeight.toFloat() * PREVIEW_WIDTH / landscapeWidth)
                .roundToInt()
                .coerceAtLeast(1)
            timesUs.forEach { timeUs ->
                val bitmap = retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    PREVIEW_WIDTH,
                    previewHeight,
                ) ?: retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )?.let { full ->
                    Bitmap.createScaledBitmap(full, PREVIEW_WIDTH, previewHeight, true)
                        .also { full.recycle() }
                } ?: run {
                    skipped++
                    return@forEach
                }
                if (width == 0) {
                    width = bitmap.width
                    height = bitmap.height
                }
                val file = File(temp, "%05d.jpg".format(savedTimes.size))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bitmap.recycle()
                savedTimes += timeUs / 1_000
            }
            if (savedTimes.isEmpty()) return null
            val index = PreviewIndex(
                width = width,
                height = height,
                timesMs = savedTimes,
                sourceBytes = video.length(),
                sourceModifiedMs = video.lastModified(),
            )
            File(temp, "index.json").writeText(json.encodeToString(index))
            if (!temp.renameTo(directory)) return null
            Log.i(
                TAG,
                "built ${directory.name}: ${savedTimes.size} frames" +
                    (if (skipped > 0) " ($skipped skipped)" else "") +
                    " in ${SystemClock.elapsedRealtime() - started} ms",
            )
            ReplayPreview(directory, index, cacheKey)
        } catch (failure: Exception) {
            Log.w(TAG, "build failed for ${video.name}", failure)
            null
        } finally {
            retriever.release()
            if (temp.exists()) temp.deleteRecursively()
        }
    }

    private fun keyframeTimes(video: File): List<Long> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(video.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: return emptyList()
            extractor.selectTrack(track)
            val times = mutableListOf<Long>()
            while (true) {
                val time = extractor.sampleTime
                if (time < 0) break
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    times += time
                }
                if (!extractor.advance()) break
            }
            times.distinct().sorted()
        } catch (failure: Exception) {
            Log.w(TAG, "cannot read keyframes from ${video.name}", failure)
            emptyList()
        } finally {
            extractor.release()
        }
    }

    internal fun previewDirectory(video: File): File =
        File(video.parentFile, video.nameWithoutExtension + ".preview")

    private fun tempDirectory(video: File): File =
        File(video.parentFile, video.nameWithoutExtension + ".preview.tmp")

    private fun cacheKey(video: File): String =
        "${video.absolutePath}:${video.length()}:${video.lastModified()}"

    companion object {
        @Volatile
        private var shared: ReplayPreviewCache? = null

        fun get(context: Context): ReplayPreviewCache =
            shared ?: synchronized(this) {
                shared ?: ReplayPreviewCache(context).also { shared = it }
            }
    }
}
