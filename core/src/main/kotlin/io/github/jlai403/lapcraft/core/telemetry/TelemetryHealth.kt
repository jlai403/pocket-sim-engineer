package io.github.jlai403.lapcraft.core.telemetry

data class StreamHealth(val healthy: Boolean, val reason: String)

object TelemetryHealth {
    const val MIN_PACKETS_PER_SECOND = 50.0
    const val MAX_PACKET_AGE_MS = 2_000L

    fun evaluate(
        connected: Boolean,
        packetsPerSecond: Double,
        lastPacketAgeMs: Long,
    ): StreamHealth = when {
        !connected -> StreamHealth(false, "Waiting for packets")
        lastPacketAgeMs < 0 || lastPacketAgeMs > MAX_PACKET_AGE_MS ->
            StreamHealth(false, "Packets are stale")
        packetsPerSecond < MIN_PACKETS_PER_SECOND ->
            StreamHealth(false, "Packet rate is low")
        else -> StreamHealth(true, "Streaming")
    }
}
