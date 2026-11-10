package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.abs

data class CaptureUiState(
    val ps5Ip: String = "",
    val status: String = "Waiting for telemetry",
    val packetsPerSecond: Double = 0.0,
    val lapCount: Int = 0,
    val speedMps: Float = 0f,
    val recording: Boolean = false,
    val audioWarning: String? = null,
    val notice: String? = null,
)

sealed interface Screen {
    data object Library : Screen
    data class Session(val sessionId: String) : Screen
    data class Player(val sessionId: String, val lapIndex: Int) : Screen
    data object Capture : Screen
    data object Settings : Screen
}

fun Sample.lapTimeMs(lap: LapMetadata): Int =
    ((timeNanos - lap.startNanos) / 1_000_000L).toInt()

fun List<Sample>.lapWindow(lap: LapMetadata): List<Sample> =
    filter { it.timeNanos >= lap.startNanos && it.timeNanos <= lap.endNanos }

fun List<Sample>.nearest(lap: LapMetadata, timeMs: Int): Sample? =
    minByOrNull { abs(it.lapTimeMs(lap) - timeMs) }

fun lapTimeLabel(ms: Int): String {
    if (ms <= 0) return "-"
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1_000
    val millis = ms % 1_000
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}
