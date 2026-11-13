package io.github.jlai403.lapcraft.core.protocol

import io.github.jlai403.lapcraft.core.model.Sample
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI

object PacketAParser {
    private const val STEERING_OFFSET = 0x128
    private const val STEERING_END = STEERING_OFFSET + 4

    fun parse(decrypted: ByteArray, receivedAtNanos: Long): Sample {
        val b = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN)
        return Sample(
            timeNanos = receivedAtNanos,
            speedMps = b.getFloat(0x4C),
            throttle = decrypted[0x91].toInt() and 0xFF,
            brake = decrypted[0x92].toInt() and 0xFF,
            gear = decrypted[0x90].toInt() and 0x0F,
            rpm = b.getFloat(0x3C),
            positionX = b.getFloat(0x04),
            positionY = b.getFloat(0x08),
            positionZ = b.getFloat(0x0C),
            lapCount = b.getShort(0x74).toInt(),
            bestLapTimeMs = b.getInt(0x78),
            lastLapTimeMs = b.getInt(0x7C),
            flags = b.getShort(0x8E).toInt() and 0xFFFF,
            carCode = b.getInt(0x124),
            fuel = b.getFloat(0x44),
            tyreTempFL = b.getFloat(0x60),
            tyreTempFR = b.getFloat(0x64),
            tyreTempRL = b.getFloat(0x68),
            tyreTempRR = b.getFloat(0x6C),
            packetId = b.getInt(0x70),
            steering = steering(decrypted.size, b),
            currentLapTimeMs = 0,
        )
    }

    /** Packet B and later carry the wheel angle in radians, positive to the left, held at ±π. */
    private fun steering(packetSize: Int, b: ByteBuffer): Float {
        if (packetSize < STEERING_END) return 0f
        val radians = b.getFloat(STEERING_OFFSET)
        if (!radians.isFinite()) return 0f
        return (radians / PI.toFloat()).coerceIn(-1f, 1f)
    }
}
