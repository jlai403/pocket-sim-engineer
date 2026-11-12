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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.github.jlai403.pocketsimengineer.app.MainActivity
import io.github.jlai403.pocketsimengineer.app.capture.LapSaver
import io.github.jlai403.pocketsimengineer.app.recording.StreamNotificationText
import io.github.jlai403.pocketsimengineer.app.track.TrackCatalog
import io.github.jlai403.pocketsimengineer.app.Diagnostics
import io.github.jlai403.pocketsimengineer.core.capture.CaptureState
import io.github.jlai403.pocketsimengineer.core.capture.LapCaptureCoordinator
import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.lap.LapDetector
import io.github.jlai403.pocketsimengineer.core.network.Ipv4
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Decoder
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class TelemetryService : Service() {
    companion object {
        const val ACTION_START = "io.github.jlai403.pocketsimengineer.action.START_TELEMETRY"
        const val ACTION_STOP = "io.github.jlai403.pocketsimengineer.action.STOP_TELEMETRY"
        const val ACTION_CAPTURE = "io.github.jlai403.pocketsimengineer.action.CAPTURE_LAP"
        const val ACTION_CANCEL_CAPTURE = "io.github.jlai403.pocketsimengineer.action.CANCEL_CAPTURE"
        const val EXTRA_IP = "ps5_ip"
        private const val CHANNEL_ID = "telemetry"
        private const val NOTIFICATION_ID = 1001

        val state = MutableStateFlow(TelemetryUiState())
        val events = MutableStateFlow<List<TelemetryEvent>>(emptyList())
        val completedLaps = MutableStateFlow(0)
        val capture = MutableStateFlow<CaptureState>(CaptureState.Idle)
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
    private lateinit var coordinator: LapCaptureCoordinator
    private val lapDetector = LapDetector()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Telemetry", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = try {
        handleCommand(intent)
    } catch (error: Throwable) {
        Log.e("PSE", "telemetry onStartCommand failed", error)
        running.value = false
        lastError.value = "Telemetry failed: ${error.message ?: error::class.java.simpleName}"
        state.value = TelemetryUiState(status = lastError.value.orEmpty())
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { stopSelf() }
        START_NOT_STICKY
    }

    private fun handleCommand(intent: Intent?): Int {
        when (intent?.action) {
            ACTION_CAPTURE -> {
                val start = lapDetector.currentLapStartNanos
                if (::coordinator.isInitialized &&
                    state.value.connected &&
                    lapDetector.currentLapStartObserved &&
                    start != null
                ) {
                    coordinator.requestCapture(start)
                } else {
                    capture.value = CaptureState.Rejected("Cross the start line first")
                }
                return START_NOT_STICKY
            }
            ACTION_CANCEL_CAPTURE -> {
                if (::coordinator.isInitialized) coordinator.cancelCapture()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                if (::coordinator.isInitialized) coordinator.onDisconnect()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val host = Ipv4.normalize(intent.getStringExtra(EXTRA_IP).orEmpty())
                Log.i("PSE", "telemetry start · $host")
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
                if (!::coordinator.isInitialized) {
                    coordinator = LapCaptureCoordinator(
                        sink = LapSaver(
                            sessionsDir = File(filesDir, "sessions"),
                            trackName = TrackCatalog(this)::identify,
                        ),
                    )
                    serviceScope.launch {
                        coordinator.state.collect { capture.value = it }
                    }
                } else {
                    coordinator.onDisconnect()
                }
                val newSource = UdpTelemetrySource(
                    host = host,
                    decoder = Gt7Decoder(),
                    lapDetector = lapDetector,
                    stats = TelemetryStats(),
                    onSample = { coordinator.onSample(it) },
                    onLap = {
                        coordinator.onBoundary(it)
                        handleLap(it)
                    },
                    onState = { publishState(it) },
                    onStatus = { state.value = state.value.copy(status = it) },
                )
                val failure = newSource.start()
                if (failure != null) {
                    Diagnostics.log("telemetry start failed: $failure")
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
                Diagnostics.log("telemetry listening on UDP 33740 · $host")
                recordEvent("Listening on UDP ${Gt7Protocol.PORT_RECEIVE} · $host")
                lastNotificationKey = null
                updateNotification(force = true)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Diagnostics.log("telemetry service destroyed")
        source?.stop()
        source = null
        if (::coordinator.isInitialized) coordinator.onDisconnect()
        serviceScope.cancel()
        capture.value = CaptureState.Idle
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
            Diagnostics.log(if (next.connected) "telemetry connected" else "telemetry disconnected")
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
        syncWakeLock()
        updateNotification()
        if (next.connected && !previous.connected) {
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
        val summary = StreamNotificationText.streamSummary(telemetry, capture.value)
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
        if (running.value) {
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
        val cancelCapture = PendingIntent.getService(
            this,
            2,
            Intent(this, TelemetryService::class.java).setAction(ACTION_CANCEL_CAPTURE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
            .apply { details?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) } }
        if (capture.value is CaptureState.Capturing) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel capture", cancelCapture)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnect)
        return builder.build()
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
