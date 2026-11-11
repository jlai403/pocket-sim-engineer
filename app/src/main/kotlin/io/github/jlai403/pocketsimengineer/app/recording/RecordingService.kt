package io.github.jlai403.pocketsimengineer.app.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.jlai403.pocketsimengineer.app.MainActivity
import io.github.jlai403.pocketsimengineer.app.service.TelemetryService
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.Diagnostics
import io.github.jlai403.pocketsimengineer.core.clock.Clock
import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.SessionMetadata
import io.github.jlai403.pocketsimengineer.core.session.SessionWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class RecordingService : Service() {
    companion object {
        const val ACTION_ARM = "io.github.jlai403.pocketsimengineer.action.ARM_RECORDING"
        const val ACTION_START = "io.github.jlai403.pocketsimengineer.action.START_RECORDING"
        const val ACTION_STOP = "io.github.jlai403.pocketsimengineer.action.STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_AUTO_STOP_MS = "auto_stop_ms"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1002
        const val SETTINGS = "settings"
        const val KEEP_RAW = "keep_raw"

        val isRecording = MutableStateFlow(false)
        val lastOutput = MutableStateFlow<String?>(null)
        val lastAudioOutput = MutableStateFlow<String?>(null)
        val audioSilent = MutableStateFlow<Boolean?>(null)
        val notice = MutableStateFlow<String?>(null)
        val activeSessionDir = MutableStateFlow<String?>(null)

        private const val MIN_FREE_BYTES = 2_000_000_000L

        @Volatile
        var instance: RecordingService? = null
            private set

        private val finalizeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var finishing = false
    private var autoStopJob: Job? = null
    private var tickJob: Job? = null
    private var controller: RecordingController? = null
    private var recorder: ScreenRecorder? = null
    private var audioCapture: AudioCapture? = null
    private var projection: MediaProjection? = null
    private var sessionWriter: SessionWriter? = null
    private var lastNotificationKey: String? = null
    private var lastCarCode = 0
    private var audioStartedNanos = 0L
    private var wasConnected = false
    private var lastToastAtNanos = 0L
    private var lastOnTrack: Boolean? = null
    private var packetsLostLogged = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopEverything()
            ACTION_ARM -> arm(intent)
            ACTION_START -> recordTest(intent)
        }
        return START_NOT_STICKY
    }

    fun onTelemetry(sample: Sample) {
        val current = controller ?: return
        try {
            val onTrack = sample.flags and 1 != 0
            if (lastOnTrack != onTrack) {
                Diagnostics.log("telemetry onTrack=$onTrack lap=${sample.lapCount}")
                lastOnTrack = onTrack
            }
            packetsLostLogged = false
            if (onTrack) {
                current.onEvent(RecordingEvent.CarOnTrack(sample.lapCount, sample.carCode))
            } else {
                current.onEvent(RecordingEvent.OffTrack)
            }
            if (current.state.value is RecordingState.Recording) {
                sessionWriter?.addSample(sample)
                if (sample.carCode > 0) updateCar(sample.carCode)
            }
        } catch (error: Exception) {
            notice.value = "Recorder failed: ${error.message}"
            current.onEvent(RecordingEvent.RecorderError(error.message ?: "recorder-failed"))
            stopEverything()
        }
    }

    fun onNoPackets() {
        if (!packetsLostLogged) {
            Diagnostics.log("telemetry lost")
            packetsLostLogged = true
        }
        controller?.onEvent(RecordingEvent.NoPackets)
    }

    fun onLap(boundary: LapBoundary) {
        Diagnostics.log("lap ${boundary.lapNumber} timeMs=${boundary.lapTimeMs}")
        if (!isRecording.value) return
        runCatching {
            sessionWriter?.writeLap(
                boundary = boundary,
                videoFile = "raw.mp4",
                videoStartOffsetMs = 0,
            )
        }.onFailure { notice.value = "Lap write failed: ${it.message}" }
    }

    private fun updateCar(carCode: Int) {
        if (carCode == lastCarCode) return
        lastCarCode = carCode
        runCatching {
            sessionWriter?.updateMetadata {
                it.copy(carCode = carCode, carName = "Car #$carCode")
            }
        }
    }

    private fun arm(intent: Intent) {
        startForeground(
            NOTIFICATION_ID,
            notification("Armed"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        if (finishing || projection != null || sessionWriter != null) return
        val mediaProjection = projectionFrom(intent) ?: run {
            stopEverything()
            return
        }
        Diagnostics.log("arm")
        projection = mediaProjection
        notice.value = null
        audioSilent.value = null
        lastCarCode = 0
        wasConnected = false
        lastToastAtNanos = 0
        lastOnTrack = null
        packetsLostLogged = false
        val settings = SettingsStore(this)
        val sessionId = "session-${System.currentTimeMillis()}"
        val sessionDir = File(filesDir, "sessions/$sessionId")
        sessionWriter = SessionWriter(
            sessionDir,
            SessionMetadata(
                sessionId = sessionId,
                createdAt = Instant.now().toString(),
                trackLabel = "Unknown",
                carCode = 0,
                carName = "Unknown",
                rawFile = "raw.mp4",
                audioFile = "audio.m4a",
                keepRaw = settings.keepRaw,
            ),
        )
        File(sessionDir, SessionFinalizer.IN_PROGRESS).writeText(Process.myPid().toString())
        lastOutput.value = sessionDir.absolutePath
        activeSessionDir.value = sessionDir.absolutePath
        controller = RecordingController(
            clock = object : Clock {
                override fun nanoTime(): Long = System.nanoTime()
            },
            recorder = object : Recorder {
                override val isRecording: Boolean get() = recorder?.isRecording == true
                override fun start() = startCapture()
                override fun stop() = stopEverything()
            },
        ).also {
            it.onEvent(RecordingEvent.Arm)
            it.onEvent(RecordingEvent.PermissionGranted)
        }
        startTicking()
        updateNotification()
    }

    private fun recordTest(intent: Intent) {
        val autoStopMs = intent.getLongExtra(EXTRA_AUTO_STOP_MS, 0L)
        startForeground(
            NOTIFICATION_ID,
            notification("Recording test clip"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        if (finishing || projection != null) return
        val mediaProjection = projectionFrom(intent) ?: run {
            stopEverything()
            return
        }
        projection = mediaProjection
        notice.value = null
        runCatching { startCapture() }
            .onFailure { notice.value = "Recording failed: ${it.message}" }
        if (autoStopMs > 0) {
            autoStopJob = scope.launch {
                delay(autoStopMs)
                stopEverything()
            }
        }
    }

    private fun projectionFrom(intent: Intent): MediaProjection? {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent ?: return null
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = manager.getMediaProjection(resultCode, resultData) ?: return null
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopEverything()
                }
            },
            null,
        )
        return mediaProjection
    }

    private fun startCapture() {
        if (recorder != null) return
        val mediaProjection = projection ?: return
        val stamp = System.currentTimeMillis()
        val sessionDir = sessionWriter?.sessionDir()
        val file = if (sessionDir != null) {
            File(sessionDir, "raw.mp4")
        } else {
            File(filesDir, "raw-$stamp.mp4")
        }
        lastOutput.value = file.absolutePath
        val settings = SettingsStore(this)
        val quality = settings.quality
        val config = VideoEncoderConfig(
            width = quality.width,
            height = quality.height,
            fps = 60,
            bitrate = settings.bitrateMbps * 1_000_000,
            mime = settings.codec.mime,
        )
        val captureStartNanos = System.nanoTime()
        val newRecorder = ScreenRecorder(mediaProjection, file, config) { message ->
            onRecorderError(message)
        }
        try {
            newRecorder.start()
            recorder = newRecorder
            sessionWriter?.updateMetadata {
                it.copy(recordingStartNanos = captureStartNanos)
            }
        } catch (error: Exception) {
            newRecorder.stop()
            throw error
        }

        audioSilent.value = null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val audioFile = if (sessionDir != null) {
                File(sessionDir, "audio.m4a")
            } else {
                File(filesDir, "raw-audio-$stamp.m4a")
            }
            lastAudioOutput.value = audioFile.absolutePath
            try {
                audioCapture = AudioCapture(mediaProjection, audioFile) { message ->
                    onRecorderError(message)
                }.also { it.start() }
                audioStartedNanos = System.nanoTime()
            } catch (_: Exception) {
                audioSilent.value = true
            }
        } else {
            audioSilent.value = true
        }
        isRecording.value = true
        Diagnostics.log("capture started file=${file.name}")
        updateNotification()
    }

    private fun stopCapture() {
        if (isRecording.value) Diagnostics.log("capture stopped")
        audioCapture?.stop()
        audioSilent.value = audioCapture?.silent ?: true
        audioCapture = null
        audioStartedNanos = 0
        recorder?.stop()
        recorder = null
        isRecording.value = false
    }

    private fun onRecorderError(message: String) {
        notice.value = message
        controller?.onEvent(RecordingEvent.RecorderError(message))
        scope.launch { toast(message) }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val wasRecording = isRecording.value
                controller?.tick()
                if (wasRecording && !isRecording.value) {
                    Diagnostics.log("auto-stop after off-track or no packets")
                }
                maybeStreamToasts()
                updateNotification()
                val audio = audioCapture
                if (isRecording.value && audio != null && audioStartedNanos > 0 &&
                    System.nanoTime() - audioStartedNanos > 2_000_000_000L && audio.frames == 0L
                ) {
                    audioSilent.value = true
                    notice.value = "No audio frames — continuing with silent video."
                    finalizeScope.launch {
                        audio.stop()
                        if (audioCapture === audio) audioCapture = null
                    }
                }
                if (isRecording.value && filesDir.usableSpace < MIN_FREE_BYTES) {
                    notice.value = "Storage below 2 GB — recording stopped."
                    stopEverything()
                    break
                }
                updateNotification()
            }
        }
    }

    @Synchronized
    private fun stopEverything() {
        if (finishing) return
        Diagnostics.log("stop requested recording=${isRecording.value} writer=${sessionWriter != null}")
        finishing = true
        autoStopJob?.cancel()
        autoStopJob = null
        tickJob?.cancel()
        tickJob = null
        val controllerToStop = controller
        controller = null
        val writer = sessionWriter
        sessionWriter = null
        val projectionToStop = projection
        projection = null
        wasConnected = false
        val keepRaw = SettingsStore(this).keepRaw
        setNotification("Finalizing…")

        finalizeScope.launch {
            try {
                controllerToStop?.onEvent(RecordingEvent.Stop)
                stopCapture()
                withContext(Dispatchers.Main) { runCatching { projectionToStop?.stop() } }
                if (writer != null) {
                    writer.finalize(rawDeleted = false)
                    Diagnostics.log("finalize laps=${writer.laps().size}")
                    SessionFinalizer.finish(writer, keepRaw)
                    Diagnostics.log("finalized")
                }
            } catch (error: Exception) {
                notice.value = "Finalize failed: ${error.message}"
                runCatching { writer?.finalize(rawDeleted = false) }
            } finally {
                activeSessionDir.value = null
                withContext(Dispatchers.Main) {
                    runCatching { projectionToStop?.stop() }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        instance = null
        if (!finishing) stopEverything()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun maybeStreamToasts() {
        if (controller == null) return
        val telemetry = TelemetryService.state.value
        if (telemetry.connected && !wasConnected) {
            wasConnected = true
            toast("GT7 stream OK · %.0f pps".format(telemetry.packetsPerSecond))
        } else if (!telemetry.connected && wasConnected) {
            wasConnected = false
            toast("GT7 telemetry lost")
        }
    }

    private fun toast(message: String) {
        // Toasts are part of the captured display, so stay silent while the MP4 is rolling.
        if (isRecording.value) return
        val now = System.nanoTime()
        if (now - lastToastAtNanos < 3_000_000_000L) return
        lastToastAtNanos = now
        runCatching { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun updateNotification() {
        val state = controller?.state?.value
        val telemetry = TelemetryService.state.value
        val base = when (state) {
            is RecordingState.Armed -> "Armed"
            is RecordingState.Recording -> "Recording lap ${state.lapCount}"
            is RecordingState.Error -> "Error: ${state.message}"
            is RecordingState.Stopping -> "Stopping"
            else -> if (isRecording.value) "Recording" else "Armed"
        }
        val summary = StreamNotificationText.summary(base, telemetry)
        val details = StreamNotificationText.details(
            telemetry,
            TelemetryService.events.value.firstOrNull()?.message,
        )
        setNotification(summary, details)
    }

    private fun setNotification(text: String, details: String? = null) {
        val key = text + "|" + details.orEmpty()
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text, details))
    }

    private fun notification(text: String, details: String? = null): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Sim Engineer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .apply { details?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) } }
            .build()
    }
}
