package io.github.jlai403.lapcraft.core.session

import io.github.jlai403.lapcraft.core.model.Sample
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LapTelemetryCodec {
    private const val HEADER_SIZE = 32
    private const val SAMPLE_SIZE = 79
    private const val VERSION = 1
    private const val SAMPLE_RATE_HZ = 60

    fun encode(samples: List<Sample>): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + samples.size * SAMPLE_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(0, 'G'.code.toByte())
        buffer.put(1, '7'.code.toByte())
        buffer.put(2, 'L'.code.toByte())
        buffer.put(3, 'P'.code.toByte())
        buffer.putShort(4, VERSION.toShort())
        buffer.putShort(6, HEADER_SIZE.toShort())
        buffer.putInt(8, samples.size)
        buffer.putShort(12, SAMPLE_RATE_HZ.toShort())
        buffer.putShort(14, 0)
        buffer.putLong(16, samples.firstOrNull()?.timeNanos ?: 0L)
        buffer.putLong(24, samples.lastOrNull()?.timeNanos ?: 0L)

        var offset = HEADER_SIZE
        for (sample in samples) {
            buffer.putLong(offset, sample.timeNanos); offset += 8
            buffer.putFloat(offset, sample.speedMps); offset += 4
            buffer.put(offset, sample.throttle.toByte()); offset += 1
            buffer.put(offset, sample.brake.toByte()); offset += 1
            buffer.put(offset, sample.gear.toByte()); offset += 1
            buffer.putFloat(offset, sample.rpm); offset += 4
            buffer.putFloat(offset, sample.positionX); offset += 4
            buffer.putFloat(offset, sample.positionY); offset += 4
            buffer.putFloat(offset, sample.positionZ); offset += 4
            buffer.putShort(offset, sample.lapCount.toShort()); offset += 2
            buffer.putInt(offset, sample.lastLapTimeMs); offset += 4
            buffer.putInt(offset, sample.bestLapTimeMs); offset += 4
            buffer.putShort(offset, sample.flags.toShort()); offset += 2
            buffer.putInt(offset, sample.carCode); offset += 4
            buffer.putFloat(offset, sample.fuel); offset += 4
            buffer.putFloat(offset, sample.tyreTempFL); offset += 4
            buffer.putFloat(offset, sample.tyreTempFR); offset += 4
            buffer.putFloat(offset, sample.tyreTempRL); offset += 4
            buffer.putFloat(offset, sample.tyreTempRR); offset += 4
            buffer.putInt(offset, sample.packetId); offset += 4
            buffer.putFloat(offset, sample.steering); offset += 4
            buffer.putInt(offset, sample.currentLapTimeMs); offset += 4
        }

        return buffer.array()
    }

    fun decode(bytes: ByteArray): List<Sample> {
        require(bytes.size >= HEADER_SIZE) { "telemetry header truncated" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes[0] == 'G'.code.toByte() &&
            bytes[1] == '7'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() &&
            bytes[3] == 'P'.code.toByte()) { "bad telemetry magic" }
        require(buffer.getShort(4).toInt() == VERSION) { "unsupported telemetry version" }
        require(buffer.getShort(6).toInt() == HEADER_SIZE) { "bad telemetry header size" }

        val sampleCount = buffer.getInt(8)
        require(bytes.size == HEADER_SIZE + sampleCount * SAMPLE_SIZE) { "bad telemetry length" }

        val samples = ArrayList<Sample>(sampleCount)
        var offset = HEADER_SIZE
        repeat(sampleCount) {
            val timeNanos = buffer.getLong(offset); offset += 8
            val speedMps = buffer.getFloat(offset); offset += 4
            val throttle = bytes[offset].toInt() and 0xFF; offset += 1
            val brake = bytes[offset].toInt() and 0xFF; offset += 1
            val gear = bytes[offset].toInt(); offset += 1
            val rpm = buffer.getFloat(offset); offset += 4
            val positionX = buffer.getFloat(offset); offset += 4
            val positionY = buffer.getFloat(offset); offset += 4
            val positionZ = buffer.getFloat(offset); offset += 4
            val lapCount = buffer.getShort(offset).toInt(); offset += 2
            val lastLapTimeMs = buffer.getInt(offset); offset += 4
            val bestLapTimeMs = buffer.getInt(offset); offset += 4
            val flags = buffer.getShort(offset).toInt() and 0xFFFF; offset += 2
            val carCode = buffer.getInt(offset); offset += 4
            val fuel = buffer.getFloat(offset); offset += 4
            val tyreTempFL = buffer.getFloat(offset); offset += 4
            val tyreTempFR = buffer.getFloat(offset); offset += 4
            val tyreTempRL = buffer.getFloat(offset); offset += 4
            val tyreTempRR = buffer.getFloat(offset); offset += 4
            val packetId = buffer.getInt(offset); offset += 4
            val steering = buffer.getFloat(offset); offset += 4
            val currentLapTimeMs = buffer.getInt(offset); offset += 4

            samples += Sample(
                timeNanos = timeNanos,
                speedMps = speedMps,
                throttle = throttle,
                brake = brake,
                gear = gear,
                rpm = rpm,
                positionX = positionX,
                positionY = positionY,
                positionZ = positionZ,
                lapCount = lapCount,
                bestLapTimeMs = bestLapTimeMs,
                lastLapTimeMs = lastLapTimeMs,
                flags = flags,
                carCode = carCode,
                fuel = fuel,
                tyreTempFL = tyreTempFL,
                tyreTempFR = tyreTempFR,
                tyreTempRL = tyreTempRL,
                tyreTempRR = tyreTempRR,
                packetId = packetId,
                steering = steering,
                currentLapTimeMs = currentLapTimeMs,
            )
        }
        return samples
    }
}
