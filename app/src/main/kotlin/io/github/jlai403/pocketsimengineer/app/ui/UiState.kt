package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.app.service.TelemetryEvent
import io.github.jlai403.pocketsimengineer.core.capture.CaptureState
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.roundToInt
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
    data class ImportPick(val uri: String) : Screen
    data class ImportSync(
        val sessionId: String,
        val lapIndex: Int,
        val sourceUri: String?,
        val returnToLibrary: Boolean = false,
    ) : Screen
    data object Record : Screen
    data object Settings : Screen
}

fun Sample.lapTimeMs(lap: LapMetadata): Int =
    ((timeNanos - lap.startNanos) / 1_000_000L).toInt()

fun List<Sample>.lapWindow(lap: LapMetadata): List<Sample> =
    filter { it.timeNanos >= lap.startNanos && it.timeNanos <= lap.endNanos }

/**
 * The sample at [timeMs], interpolated between the two samples that bracket it.
 * Values that only step (gear) come from the nearest sample. Times outside the
 * window clamp to its ends.
 */
fun List<Sample>.sampleAt(lap: LapMetadata, timeMs: Int): Sample? {
    if (isEmpty()) return null
    val index = binarySearchBy(timeMs) { it.lapTimeMs(lap) }
    if (index >= 0) return this[index]
    val insert = -index - 1
    if (insert <= 0) return first()
    if (insert >= size) return last()
    val a = this[insert - 1]
    val b = this[insert]
    val aMs = a.lapTimeMs(lap)
    val bMs = b.lapTimeMs(lap)
    if (bMs <= aMs) return a
    val t = (timeMs - aMs).toFloat() / (bMs - aMs).toFloat()
    return a.copy(
        timeNanos = a.timeNanos + ((b.timeNanos - a.timeNanos).toDouble() * t).toLong(),
        speedMps = a.speedMps + (b.speedMps - a.speedMps) * t,
        throttle = (a.throttle + (b.throttle - a.throttle) * t).roundToInt(),
        brake = (a.brake + (b.brake - a.brake) * t).roundToInt(),
        rpm = a.rpm + (b.rpm - a.rpm) * t,
        gear = if (t < 0.5f) a.gear else b.gear,
        positionX = a.positionX + (b.positionX - a.positionX) * t,
        positionY = a.positionY + (b.positionY - a.positionY) * t,
        positionZ = a.positionZ + (b.positionZ - a.positionZ) * t,
    )
}

fun formatDate(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(iso))
}.getOrDefault(iso)

/** Short feed-style date, e.g. `Nov 11`. */
fun shortDateLabel(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("MMM d")
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

/** Clip and lap timeline label with millisecond precision; zero prints as `0:00.000`. */
fun formatMillis(ms: Long): String {
    if (ms < 0) return "-"
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1_000
    val millis = ms % 1_000
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}
