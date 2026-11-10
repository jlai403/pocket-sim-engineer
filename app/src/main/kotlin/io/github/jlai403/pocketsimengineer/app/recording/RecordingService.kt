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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.jlai403.pocketsimengineer.app.MainActivity
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

        val isRecording = MutableStateFlow(false)
        val lastOutput = MutableStateFlow<String?>(null)
        val lastAudioOutput = MutableStateFlow<String?>(null)
        val audioSilent = MutableStateFlow<Boolean?>(null)

        @Volatile
        var instance: RecordingService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoStopJob: Job? = null
    private var tickJob: Job? = null
    private var controller: RecordingController? = null
    private var recorder: ScreenRecorder? = null
    private var audioCapture: AudioCapture? = null
    private var projection: MediaProjection? = null
    private var sessionWriter: SessionWriter? = null

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
        sessionWriter?.addSample(sample)
        val current = controller ?: return
        if (sample.flags and 1 != 0) {
            current.onEvent(RecordingEvent.CarOnTrack(sample.lapCount, sample.carCode))
        } else {
            current.onEvent(RecordingEvent.OffTrack)
        }
        updateNotification()
    }

    fun onNoPackets() {
        controller?.onEvent(RecordingEvent.NoPackets)
        updateNotification()
    }

    fun onLap(boundary: LapBoundary) {
        sessionWriter?.writeLap(
            boundary = boundary,
            videoFile = "raw.mp4",
            videoStartOffsetMs = 0,
        )
    }

    private fun arm(intent: Intent) {
        startForeground(
            NOTIFICATION_ID,
            notification("Armed"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        val mediaProjection = projectionFrom(intent) ?: run {
            stopEverything()
            return
        }
        projection = mediaProjection
        val sessionId = "session-${Instant.now().epochSecond}"
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
            ),
        )
        lastOutput.value = sessionDir.absolutePath
        controller = RecordingController(
            clock = object : Clock {
                override fun nanoTime(): Long = System.nanoTime()
            },
            recorder = object : Recorder {
                override val isRecording: Boolean get() = recorder?.isRecording == true
                override fun start() = startCapture()
                override fun stop() = stopCapture()
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
        val mediaProjection = projectionFrom(intent) ?: run {
            stopEverything()
            return
        }
        projection = mediaProjection
        startCapture()
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
        val stamp = Instant.now().epochSecond
        val sessionDir = sessionWriter?.sessionDir()
        val file = if (sessionDir != null) {
            File(sessionDir, "raw.mp4")
        } else {
            File(filesDir, "raw-$stamp.mp4")
        }
        lastOutput.value = file.absolutePath
        recorder = ScreenRecorder(mediaProjection, file).also { it.start() }

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
                audioCapture = AudioCapture(mediaProjection, audioFile).also { it.start() }
            } catch (_: Exception) {
                audioSilent.value = true
            }
        } else {
            audioSilent.value = true
        }
        isRecording.value = true
        updateNotification()
    }

    private fun stopCapture() {
        audioCapture?.stop()
        audioSilent.value = audioCapture?.silent ?: true
        audioCapture = null
        recorder?.stop()
        recorder = null
        isRecording.value = false
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(500)
                controller?.tick()
                updateNotification()
            }
        }
    }

    private fun stopEverything() {
        autoStopJob?.cancel()
        autoStopJob = null
        tickJob?.cancel()
        tickJob = null
        controller?.onEvent(RecordingEvent.Stop)
        controller = null
        stopCapture()
        sessionWriter?.finalize(rawDeleted = false)
        sessionWriter = null
        val currentProjection = projection
        projection = null
        currentProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        autoStopJob?.cancel()
        tickJob?.cancel()
        controller?.onEvent(RecordingEvent.Stop)
        controller = null
        stopCapture()
        sessionWriter?.finalize(rawDeleted = false)
        sessionWriter = null
        val currentProjection = projection
        projection = null
        currentProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        val state = controller?.state?.value
        val text = when (state) {
            is RecordingState.Armed -> "Armed"
            is RecordingState.Recording -> "Recording lap ${state.lapCount}"
            is RecordingState.Error -> "Error: ${state.message}"
            is RecordingState.Stopping -> "Stopping"
            else -> if (isRecording.value) "Recording" else "Armed"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
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
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
    }
}
