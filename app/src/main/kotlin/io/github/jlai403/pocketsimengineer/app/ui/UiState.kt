package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.app.service.TelemetryEvent
import io.github.jlai403.pocketsimengineer.core.capture.CaptureState
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.abs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CaptureUiState(
    val ps5Ip: String = "",
    val status: String = "Waiting for telemetry",
    val telemetryRunning: Boolean = false,
    val telemetryError: String? = null,
    val connected: Boolean = false,
    val packetsPerSecond: Double = 0.0,
    val decodeErrors: Long = 0,
    val acceptedPackets: Long = 0,
    val rawPackets: Long = 0,
    val foreignPackets: Long = 0,
    val lastForeignSource: String = "",
    val lastDecodeFailure: String = "",
    val lastPacketAgeMs: Long = -1,
    val lapCount: Int = 0,
    val speedMps: Float = 0f,
    val gear: Int = 0,
    val throttle: Int = 0,
    val brake: Int = 0,
    val rpm: Float = 0f,
    val packetId: Int = 0,
    val carCode: Int = 0,
    val flags: Int = 0,
    val lastLapTimeMs: Int = 0,
    val lapStartObserved: Boolean = false,
    val capture: CaptureState = CaptureState.Idle,
    val notice: String? = null,
    val events: List<TelemetryEvent> = emptyList(),
    val completedLaps: Int = 0,
)

fun formatEventTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))

sealed interface Screen {
    data object Library : Screen
    data class Player(val sessionId: String, val lapIndex: Int) : Screen
    data object Live : Screen
    data object Settings : Screen
}

fun Sample.lapTimeMs(lap: LapMetadata): Int =
    ((timeNanos - lap.startNanos) / 1_000_000L).toInt()

fun List<Sample>.lapWindow(lap: LapMetadata): List<Sample> =
    filter { it.timeNanos >= lap.startNanos && it.timeNanos <= lap.endNanos }

fun List<Sample>.nearest(lap: LapMetadata, timeMs: Int): Sample? =
    minByOrNull { abs(it.lapTimeMs(lap) - timeMs) }

fun formatDate(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
}.getOrDefault(iso)

fun lapTimeLabel(ms: Int): String {
    if (ms <= 0) return "-"
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1_000
    val millis = ms % 1_000
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}
