package io.github.jlai403.pocketsimengineer.app

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
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
import io.github.jlai403.pocketsimengineer.app.service.TelemetryService
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.ui.App
import io.github.jlai403.pocketsimengineer.app.ui.CaptureUiState
import io.github.jlai403.pocketsimengineer.app.ui.PocketSimEngineerTheme
import io.github.jlai403.pocketsimengineer.app.updater.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsStore

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (!TelemetryService.running.value) {
            val saved = settings.ps5Ip
            if (saved.isNotBlank()) startTelemetry(saved)
        }
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
            val capture by TelemetryService.capture.collectAsStateWithLifecycle()
            val telemetryEvents by TelemetryService.events.collectAsStateWithLifecycle()
            val completedLaps by TelemetryService.completedLaps.collectAsStateWithLifecycle()
            val update by UpdateManager.state.collectAsStateWithLifecycle()
            var ip by rememberSaveable { mutableStateOf(settings.ps5Ip) }

            LaunchedEffect(Unit) { UpdateManager.checkIfStale(applicationContext) }

            PocketSimEngineerTheme {
                App(
                    capture = CaptureUiState(
                        ps5Ip = ip,
                        status = serviceState.status,
                        telemetryRunning = telemetryRunning,
                        telemetryError = telemetryError,
                        connected = serviceState.connected,
                        packetsPerSecond = serviceState.packetsPerSecond,
                        decodeErrors = serviceState.decodeErrors,
                        acceptedPackets = serviceState.acceptedPackets,
                        rawPackets = serviceState.rawPackets,
                        foreignPackets = serviceState.foreignPackets,
                        lastForeignSource = serviceState.lastForeignSource,
                        lastDecodeFailure = serviceState.lastDecodeFailure,
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
                        lapStartObserved = serviceState.lapStartObserved,
                        capture = capture,
                        events = telemetryEvents,
                        completedLaps = completedLaps,
                    ),
                    onSaveIp = { raw ->
                        if (settings.savePs5Ip(raw)) ip = settings.ps5Ip
                    },
                    onConnect = { raw -> connect(raw) },
                    onDisconnect = { disconnect() },
                    onCapture = { sendServiceAction(TelemetryService.ACTION_CAPTURE) },
                    onCancelCapture = { sendServiceAction(TelemetryService.ACTION_CANCEL_CAPTURE) },
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
        // Start the foreground service while the activity is definitely visible.
        // Requesting the permission first pauses us and Android rejects the start.
        Log.i("PSE", "connect requested · ${settings.ps5Ip}")
        startTelemetry(settings.ps5Ip)
        requestNotificationPermission()
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
        sendServiceAction(TelemetryService.ACTION_STOP)
    }

    private fun sendServiceAction(action: String) {
        runCatching {
            startService(Intent(this, TelemetryService::class.java).setAction(action))
        }.onFailure { Log.w("PSE", "service action failed: $action", it) }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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
