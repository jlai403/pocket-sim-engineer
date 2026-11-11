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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.github.jlai403.pocketsimengineer.app.MainActivity
import io.github.jlai403.pocketsimengineer.app.recording.RecordingService
import io.github.jlai403.pocketsimengineer.app.recording.StreamNotificationText
import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.lap.LapDetector
import io.github.jlai403.pocketsimengineer.core.network.Ipv4
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Decoder
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class TelemetryService : Service() {
    companion object {
        const val ACTION_START = "io.github.jlai403.pocketsimengineer.action.START_TELEMETRY"
        const val ACTION_STOP = "io.github.jlai403.pocketsimengineer.action.STOP_TELEMETRY"
        const val EXTRA_IP = "ps5_ip"
        private const val CHANNEL_ID = "telemetry"
        private const val NOTIFICATION_ID = 1001

        val state = MutableStateFlow(TelemetryUiState())
        val events = MutableStateFlow<List<TelemetryEvent>>(emptyList())
        val completedLaps = MutableStateFlow(0)
        val running = MutableStateFlow(false)
        val lastError = MutableStateFlow<String?>(null)

        private const val MAX_EVENTS = 12
    }

    private var source: UdpTelemetrySource? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationKey: String? = null
    private var lastNotifyNanos = 0L
    private var lastToastNanos = 0L

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
                val host = Ipv4.normalize(intent.getStringExtra(EXTRA_IP).orEmpty())
                if (host == null) {
                    state.value = TelemetryUiState(
                        status = "Enter a valid PS5 IPv4 address, for example 192.168.1.50",
                    )
                    running.value = false
                    lastError.value = state.value.status
                    startForeground(
                        NOTIFICATION_ID,
                        notification("GT7 stream · waiting"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(
                    NOTIFICATION_ID,
                    notification("GT7 stream · waiting"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
                source?.stop()
                lastError.value = null
                releaseLocks()
                acquireLocks()
                events.value = emptyList()
                completedLaps.value = 0
                state.value = TelemetryUiState(status = "Waiting for telemetry")
                val newSource = UdpTelemetrySource(
                    host = host,
                    decoder = Gt7Decoder(),
                    lapDetector = LapDetector(),
                    stats = TelemetryStats(),
                    onSample = { RecordingService.instance?.onTelemetry(it) },
                    onLap = { handleLap(it) },
                    onState = { publishState(it) },
                    onStatus = { state.value = state.value.copy(status = it) },
                )
                val failure = newSource.start()
                if (failure != null) {
                    releaseLocks()
                    running.value = false
                    lastError.value = failure
                    state.value = TelemetryUiState(status = failure)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                source = newSource
                lastError.value = null
                running.value = true
                recordEvent("Listening on UDP ${Gt7Protocol.PORT_RECEIVE} · $host")
                lastNotificationKey = null
                updateNotification(force = true)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        source?.stop()
        source = null
        releaseLocks()
        running.value = false
        state.value = TelemetryUiState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishState(next: TelemetryUiState) {
        val previous = state.value
        state.value = next

        if (next.connected != previous.connected) {
            recordEvent(
                if (next.connected) {
                    "Connected · %.0f pps".format(next.packetsPerSecond)
                } else {
                    "Waiting for packets"
                },
            )
        }
        val onTrack = next.flags and 1 != 0
        val wasOnTrack = previous.flags and 1 != 0
        if (next.connected && onTrack != wasOnTrack) {
            recordEvent(if (onTrack) "Car on track" else "Car off track")
        }
        if (!next.connected) RecordingService.instance?.onNoPackets()
        syncWakeLock()
        updateNotification()
        if (next.connected && !previous.connected && !RecordingService.isRecording.value) {
            streamToast("GT7 stream OK · %.0f pps".format(next.packetsPerSecond))
        }
    }

    private fun streamToast(message: String) {
        val now = System.nanoTime()
        if (now - lastToastNanos < 3_000_000_000L) return
        lastToastNanos = now
        runCatching { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun updateNotification(force: Boolean = false) {
        val now = System.nanoTime()
        if (!force && now - lastNotifyNanos < 1_000_000_000L) return
        lastNotifyNanos = now
        val telemetry = state.value
        val summary = StreamNotificationText.streamSummary(telemetry)
        val details = StreamNotificationText.details(
            telemetry,
            events.value.firstOrNull()?.message,
        )
        val key = summary + "|" + details
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(summary, details))
    }

    private fun syncWakeLock() {
        if (RecordingService.isRecording.value) {
            if (wakeLock?.isHeld != true) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketSimEngineer:telemetry")
                    .apply { acquire() }
            }
        } else if (wakeLock != null) {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    private fun handleLap(boundary: LapBoundary) {
        completedLaps.update { it + 1 }
        recordEvent("Lap ${boundary.lapNumber} complete · ${formatLapTime(boundary.lapTimeMs)}")
        RecordingService.instance?.onLap(boundary)
    }

    private fun recordEvent(message: String) {
        events.update { current ->
            (listOf(TelemetryEvent(System.currentTimeMillis(), message)) + current).take(MAX_EVENTS)
        }
    }

    private fun formatLapTime(ms: Int): String =
        "%d:%02d.%03d".format(ms / 60_000, (ms % 60_000) / 1_000, ms % 1_000)

    private fun notification(summary: String, details: String? = null): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, TelemetryService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(summary)
            .setContentText(
                if (state.value.connected) {
                    "Tap Disconnect to stop listening"
                } else {
                    "GT7 sends telemetry only while driving is on screen"
                },
            )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnect)
            .apply { details?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) } }
            .build()
    }

    private fun acquireLocks() {
        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketSimEngineer:wifi")
                .apply { acquire() }
        }.onFailure { lastError.value = "Wi-Fi lock failed: ${it.message}" }
    }

    private fun releaseLocks() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
        wakeLock = null
    }
}
