package io.github.jlai403.pocketsimengineer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import io.github.jlai403.pocketsimengineer.app.service.TelemetryService
import io.github.jlai403.pocketsimengineer.app.ui.App
import io.github.jlai403.pocketsimengineer.app.ui.PocketSimEngineerTheme
import io.github.jlai403.pocketsimengineer.app.ui.SpikeUiState

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val serviceState by TelemetryService.state.collectAsStateWithLifecycle()
            var ip by rememberSaveable { mutableStateOf("") }

            PocketSimEngineerTheme {
                App(
                    state = SpikeUiState(
                        ps5Ip = ip,
                        status = serviceState.status,
                        packetsPerSecond = serviceState.packetsPerSecond,
                        lapCount = serviceState.lapCount,
                        speedMps = serviceState.speedMps,
                    ),
                    onIpChanged = { ip = it },
                    onArm = {
                        requestNotificationPermission()
                        startTelemetry(ip)
                    },
                    onTestRecord = { },
                    onStop = {
                        startService(
                            Intent(this, TelemetryService::class.java)
                                .setAction(TelemetryService.ACTION_STOP),
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
