package io.github.jlai403.pocketsimengineer.app.service

class TelemetryStats(val windowNanos: Long = 1_000_000_000) {
    private val packets = ArrayDeque<Long>()
    private var errors = 0L
    private var accepted = 0L

    fun onPacket(nowNanos: Long) {
        accepted++
        packets.addLast(nowNanos)
        prune(nowNanos)
    }

    fun onDecodeError() {
        errors++
    }

    fun packetsPerSecond(nowNanos: Long): Double {
        prune(nowNanos)
        return packets.size.toDouble()
    }

    fun decodeErrors(): Long = errors

    fun acceptedPackets(): Long = accepted

    private fun prune(nowNanos: Long) {
        while (packets.isNotEmpty() && nowNanos - packets.first() > windowNanos) {
            packets.removeFirst()
        }
    }
}
