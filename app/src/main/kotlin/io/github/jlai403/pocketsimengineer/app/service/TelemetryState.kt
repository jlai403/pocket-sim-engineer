package io.github.jlai403.pocketsimengineer.app.service

data class TelemetryUiState(
    val connected: Boolean = false,
    val packetsPerSecond: Double = 0.0,
    val decodeErrors: Long = 0,
    val lapCount: Int = 0,
    val speedMps: Float = 0f,
    val gear: Int = 0,
    val lastLapTimeMs: Int = 0,
    val status: String = "Waiting for telemetry",
)
