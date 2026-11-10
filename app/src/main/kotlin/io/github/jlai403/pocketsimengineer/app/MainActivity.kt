package io.github.jlai403.pocketsimengineer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import io.github.jlai403.pocketsimengineer.app.ui.App
import io.github.jlai403.pocketsimengineer.app.ui.PocketSimEngineerTheme
import io.github.jlai403.pocketsimengineer.app.ui.SpikeUiState

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val screenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecordingService::class.java)
                    .setAction(RecordingService.ACTION_START)
                    .putExtra(RecordingService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(RecordingService.EXTRA_RESULT_DATA, result.data)
                    .putExtra(RecordingService.EXTRA_AUTO_STOP_MS, 10_000L),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val serviceState by TelemetryService.state.collectAsStateWithLifecycle()
            val recording by RecordingService.isRecording.collectAsStateWithLifecycle()
            var ip by rememberSaveable { mutableStateOf("") }

            PocketSimEngineerTheme {
                App(
                    state = SpikeUiState(
                        ps5Ip = ip,
                        status = if (recording) "Recording test clip" else serviceState.status,
                        packetsPerSecond = serviceState.packetsPerSecond,
                        lapCount = serviceState.lapCount,
                        speedMps = serviceState.speedMps,
                        recording = recording,
                    ),
                    onIpChanged = { ip = it },
                    onArm = {
                        requestNotificationPermission()
                        startTelemetry(ip)
                    },
                    onTestRecord = { requestScreenCapture() },
                    onStop = {
                        startService(
                            Intent(this, TelemetryService::class.java)
                                .setAction(TelemetryService.ACTION_STOP),
                        )
                        startService(
                            Intent(this, RecordingService::class.java)
                                .setAction(RecordingService.ACTION_STOP),
                        )
                    },
                )
            }
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

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenCapture.launch(manager.createScreenCaptureIntent())
    }

    private fun startTelemetry(ip: String) {
        if (ip.isBlank()) return
        ContextCompat.startForegroundService(
            this,
            Intent(this, TelemetryService::class.java)
                .setAction(TelemetryService.ACTION_START)
                .putExtra(TelemetryService.EXTRA_IP, ip),
        )
    }
}
