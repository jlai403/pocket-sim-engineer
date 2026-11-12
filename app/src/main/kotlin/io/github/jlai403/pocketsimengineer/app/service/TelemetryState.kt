package io.github.jlai403.pocketsimengineer.app.service

data class TelemetryUiState(
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
    val status: String = "Waiting for telemetry",
)
