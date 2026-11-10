package io.github.jlai403.pocketsimengineer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.jlai403.pocketsimengineer.app.MainActivity
import io.github.jlai403.pocketsimengineer.app.recording.RecordingService
import io.github.jlai403.pocketsimengineer.core.lap.LapDetector
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Decoder
import kotlinx.coroutines.flow.MutableStateFlow

class TelemetryService : Service() {
    companion object {
        const val ACTION_START = "io.github.jlai403.pocketsimengineer.action.START_TELEMETRY"
        const val ACTION_STOP = "io.github.jlai403.pocketsimengineer.action.STOP_TELEMETRY"
        const val EXTRA_IP = "ps5_ip"
        private const val CHANNEL_ID = "telemetry"
        private const val NOTIFICATION_ID = 1001

        val state = MutableStateFlow(TelemetryUiState())
    }

    private var source: UdpTelemetrySource? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Telemetry", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_IP)?.trim().orEmpty()
                if (host.isEmpty()) {
                    state.value = TelemetryUiState(status = "Enter the PS5 IPv4 address")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(
                    NOTIFICATION_ID,
                    notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                acquireLocks()
                source?.stop()
                source = UdpTelemetrySource(
                    host = host,
                    decoder = Gt7Decoder(),
                    lapDetector = LapDetector(),
                    stats = TelemetryStats(),
                    onSample = { RecordingService.instance?.onTelemetry(it) },
                    onLap = { },
                    onState = {
                        state.value = it
                        if (!it.connected) RecordingService.instance?.onNoPackets()
                    },
                ).also { it.start() }
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        source?.stop()
        source = null
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Sim Engineer")
            .setContentText("Listening for GT7 telemetry")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketSimEngineer:wifi")
            .apply { acquire() }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketSimEngineer:telemetry")
            .apply { acquire() }
    }

    private fun releaseLocks() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        wakeLock = null
    }
}
