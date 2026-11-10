package io.github.jlai403.pocketsimengineer.core.protocol

import io.github.jlai403.pocketsimengineer.core.model.Sample
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketAParser {
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
            steering = 0f,
            currentLapTimeMs = 0,
        )
    }
}
