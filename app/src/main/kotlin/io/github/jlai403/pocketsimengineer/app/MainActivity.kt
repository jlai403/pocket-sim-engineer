package io.github.jlai403.pocketsimengineer.app

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.jlai403.pocketsimengineer.app.recording.RecordingService
import io.github.jlai403.pocketsimengineer.app.service.TelemetryService
import io.github.jlai403.pocketsimengineer.app.updater.UpdateManager
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.ui.App
import io.github.jlai403.pocketsimengineer.app.ui.CaptureUiState
import io.github.jlai403.pocketsimengineer.app.ui.PocketSimEngineerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            val telemetryRunning by TelemetryService.running.collectAsStateWithLifecycle()
            val telemetryError by TelemetryService.lastError.collectAsStateWithLifecycle()
            val recording by RecordingService.isRecording.collectAsStateWithLifecycle()
            val audioSilent by RecordingService.audioSilent.collectAsStateWithLifecycle()
            val notice by RecordingService.notice.collectAsStateWithLifecycle()
            val telemetryEvents by TelemetryService.events.collectAsStateWithLifecycle()
            val completedLaps by TelemetryService.completedLaps.collectAsStateWithLifecycle()
            val update by UpdateManager.state.collectAsStateWithLifecycle()
            var ip by rememberSaveable { mutableStateOf(settings.ps5Ip) }

            LaunchedEffect(Unit) { UpdateManager.checkIfStale(applicationContext) }

            PocketSimEngineerTheme {
                App(
                    capture = CaptureUiState(
                        ps5Ip = ip,
                        status = if (recording) "Recording" else serviceState.status,
                        telemetryRunning = telemetryRunning,
                        telemetryError = telemetryError,
                        connected = serviceState.connected,
                        packetsPerSecond = serviceState.packetsPerSecond,
                        decodeErrors = serviceState.decodeErrors,
                        acceptedPackets = serviceState.acceptedPackets,
                        rawPackets = serviceState.rawPackets,
                        foreignPackets = serviceState.foreignPackets,
                        lastForeignSource = serviceState.lastForeignSource,
                        lastPacketAgeMs = serviceState.lastPacketAgeMs,
                        lapCount = serviceState.lapCount,
                        speedMps = serviceState.speedMps,
                        gear = serviceState.gear,
                        throttle = serviceState.throttle,
                        brake = serviceState.brake,
                        rpm = serviceState.rpm,
                        packetId = serviceState.packetId,
                        carCode = serviceState.carCode,
                        flags = serviceState.flags,
                        lastLapTimeMs = serviceState.lastLapTimeMs,
                        recording = recording,
                        audioWarning = if (audioSilent == true) {
                            "Audio unavailable — recording video only."
                        } else {
                            null
                        },
                        notice = notice,
                        events = telemetryEvents,
                        completedLaps = completedLaps,
                    ),
                    onSaveIp = { raw ->
                        if (settings.savePs5Ip(raw)) ip = settings.ps5Ip
                    },
                    onConnect = { raw -> connect(raw) },
                    onDisconnect = { disconnect() },
                    onStartRecording = { startRecording() },
                    onTestRecord = {
                        ensureAudio { requestCapture(CapturePurpose.Test) }
                    },
                    onStop = { stopRecording() },
                    update = update,
                    onCheckUpdate = { UpdateManager.check(applicationContext) },
                    onDownloadUpdate = { UpdateManager.download(applicationContext) },
                    onInstallUpdate = { UpdateManager.install(applicationContext) },
                )
            }
        }
    }

    private fun connect(raw: String) {
        if (!settings.savePs5Ip(raw)) return
        requestNotificationPermission()
        Log.i("PSE", "connect requested · ${settings.ps5Ip}")
        startTelemetry(settings.ps5Ip)
        lifecycleScope.launch {
            delay(4_000)
            if (TelemetryService.running.value || TelemetryService.lastError.value != null) return@launch
            TelemetryService.lastError.value = if (telemetryServiceRunning()) {
                "Telemetry service is alive but did not finish starting."
            } else {
                "Telemetry service did not start. Android blocked the foreground service."
            }
        }
    }

    private fun telemetryServiceRunning(): Boolean = runCatching {
        getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == TelemetryService::class.java.name }
    }.getOrDefault(false)

    private fun disconnect() {
        if (RecordingService.instance != null) {
            startService(
                Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
            )
        }
        startService(
            Intent(this, TelemetryService::class.java).setAction(TelemetryService.ACTION_STOP),
        )
    }

    private fun startRecording() {
        if (!TelemetryService.state.value.connected) return
        ensureAudio { requestCapture(CapturePurpose.Arm) }
    }

    private fun stopRecording() {
        startService(
            Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
        )
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
        val intent = Intent(this, TelemetryService::class.java)
            .setAction(TelemetryService.ACTION_START)
            .putExtra(TelemetryService.EXTRA_IP, ip)
        val foregroundFailure = runCatching {
            ContextCompat.startForegroundService(this, intent)
        }.exceptionOrNull()
        if (foregroundFailure == null) return
        Log.w("PSE", "startForegroundService failed", foregroundFailure)
        runCatching { startService(intent) }.onFailure { fallbackFailure ->
            TelemetryService.lastError.value =
                "Could not start telemetry: ${foregroundFailure.message ?: fallbackFailure.message}"
        }
    }

}
