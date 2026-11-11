package io.github.jlai403.pocketsimengineer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jlai403.pocketsimengineer.app.recording.RecordingService
import io.github.jlai403.pocketsimengineer.app.service.TelemetryService
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.ui.App
import io.github.jlai403.pocketsimengineer.app.ui.CaptureUiState
import io.github.jlai403.pocketsimengineer.app.ui.PocketSimEngineerTheme

class MainActivity : ComponentActivity() {
    private enum class CapturePurpose { Arm, Test }

    private lateinit var settings: SettingsStore
    private var capturePurpose = CapturePurpose.Test
    private var afterAudioPermission: (() -> Unit)? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val action = afterAudioPermission
        afterAudioPermission = null
        action?.invoke()
    }

    private val screenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val action = when (capturePurpose) {
            CapturePurpose.Arm -> RecordingService.ACTION_ARM
            CapturePurpose.Test -> RecordingService.ACTION_START
        }
        val intent = Intent(this, RecordingService::class.java)
            .setAction(action)
            .putExtra(RecordingService.EXTRA_RESULT_CODE, result.resultCode)
            .putExtra(RecordingService.EXTRA_RESULT_DATA, result.data)
        if (capturePurpose == CapturePurpose.Test) {
            intent.putExtra(RecordingService.EXTRA_AUTO_STOP_MS, 10_000L)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        setContent {
            val serviceState by TelemetryService.state.collectAsStateWithLifecycle()
            val recording by RecordingService.isRecording.collectAsStateWithLifecycle()
            val audioSilent by RecordingService.audioSilent.collectAsStateWithLifecycle()
            val notice by RecordingService.notice.collectAsStateWithLifecycle()
            var ip by rememberSaveable { mutableStateOf(settings.ps5Ip) }

            PocketSimEngineerTheme {
                App(
                    capture = CaptureUiState(
                        ps5Ip = ip,
                        status = if (recording) "Recording" else serviceState.status,
                        packetsPerSecond = serviceState.packetsPerSecond,
                        lapCount = serviceState.lapCount,
                        speedMps = serviceState.speedMps,
                        recording = recording,
                        audioWarning = if (audioSilent == true) {
                            "Audio unavailable — recording video only."
                        } else {
                            null
                        },
                        notice = notice,
                    ),
                    onSaveIp = { raw ->
                        if (settings.savePs5Ip(raw)) ip = settings.ps5Ip
                    },
                    onArm = { raw -> arm(raw) },
                    onTestRecord = {
                        ensureAudio { requestCapture(CapturePurpose.Test) }
                    },
                    onStop = { stopAll() },
                )
            }
        }
    }

    private fun arm(ip: String) {
        if (!settings.savePs5Ip(ip)) return
        val savedIp = settings.ps5Ip
        requestNotificationPermission()
        startTelemetry(savedIp)
        ensureAudio { requestCapture(CapturePurpose.Arm) }
    }

    private fun ensureAudio(then: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            afterAudioPermission = then
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            then()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestCapture(purpose: CapturePurpose) {
        capturePurpose = purpose
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenCapture.launch(manager.createScreenCaptureIntent())
    }

    private fun startTelemetry(ip: String) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, TelemetryService::class.java)
                .setAction(TelemetryService.ACTION_START)
                .putExtra(TelemetryService.EXTRA_IP, ip),
        )
    }

    private fun stopAll() {
        startService(
            Intent(this, TelemetryService::class.java).setAction(TelemetryService.ACTION_STOP),
        )
        startService(
            Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
        )
    }
}
