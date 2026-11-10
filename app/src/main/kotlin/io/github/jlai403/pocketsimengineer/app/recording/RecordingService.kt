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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

class RecordingService : Service() {
    companion object {
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoStopJob: Job? = null
    private var recorder: ScreenRecorder? = null
    private var audioCapture: AudioCapture? = null
    private var projection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent ?: return
        val autoStopMs = intent.getLongExtra(EXTRA_AUTO_STOP_MS, 0L)

        startForeground(
            NOTIFICATION_ID,
            notification("Recording"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = manager.getMediaProjection(resultCode, resultData) ?: return
        projection = mediaProjection
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                }
            },
            null,
        )

        val stamp = Instant.now().epochSecond
        val file = File(filesDir, "test-$stamp.mp4")
        lastOutput.value = file.absolutePath
        recorder = ScreenRecorder(mediaProjection, file).also { it.start() }

        audioSilent.value = null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val audioFile = File(filesDir, "test-audio-$stamp.m4a")
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

        if (autoStopMs > 0) {
            autoStopJob = scope.launch {
                delay(autoStopMs)
                stopRecording()
            }
        }
    }

    private fun stopRecording() {
        autoStopJob?.cancel()
        autoStopJob = null
        audioCapture?.stop()
        audioSilent.value = audioCapture?.silent ?: true
        audioCapture = null
        recorder?.stop()
        recorder = null
        val currentProjection = projection
        projection = null
        currentProjection?.stop()
        isRecording.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        audioCapture?.stop()
        audioSilent.value = audioCapture?.silent ?: true
        audioCapture = null
        recorder?.stop()
        recorder = null
        val currentProjection = projection
        projection = null
        currentProjection?.stop()
        isRecording.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Sim Engineer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
