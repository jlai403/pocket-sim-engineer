package io.github.jlai403.pocketsimengineer.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketAParserTest {
    @Test
    fun `parses packet A fields at their documented offsets`() {
        val packet = ByteArray(Gt7Protocol.PACKET_A_SIZE)
        val b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00, Gt7Protocol.MAGIC)
        b.putFloat(0x04, 1.5f)
        b.putFloat(0x08, 2.5f)
        b.putFloat(0x0C, 3.5f)
        b.putFloat(0x3C, 7000f)
        b.putFloat(0x44, 45.5f)
        b.putFloat(0x4C, 42.5f)
        b.putFloat(0x60, 80f)
        b.putFloat(0x64, 81f)
        b.putFloat(0x68, 82f)
        b.putFloat(0x6C, 83f)
        b.putInt(0x70, 99)
        b.putShort(0x74, 3)
        b.putInt(0x78, 88_000)
        b.putInt(0x7C, 90_000)
        b.putShort(0x8E, 1)
        packet[0x90] = 4
        packet[0x91] = 200.toByte()
        packet[0x92] = 10
        b.putInt(0x124, 3474)

        val sample = PacketAParser.parse(packet, receivedAtNanos = 1234L)

        assertEquals(42.5f, sample.speedMps)
        assertEquals(1.5f, sample.positionX)
        assertEquals(2.5f, sample.positionY)
        assertEquals(3.5f, sample.positionZ)
        assertEquals(7000f, sample.rpm)
        assertEquals(45.5f, sample.fuel)
        assertEquals(80f, sample.tyreTempFL)
        assertEquals(83f, sample.tyreTempRR)
        assertEquals(99, sample.packetId)
        assertEquals(3, sample.lapCount)
        assertEquals(88_000, sample.bestLapTimeMs)
        assertEquals(90_000, sample.lastLapTimeMs)
        assertEquals(1, sample.flags and 1)
        assertEquals(4, sample.gear)
        assertEquals(200, sample.throttle)
        assertEquals(10, sample.brake)
        assertEquals(3474, sample.carCode)
        assertEquals(1234L, sample.timeNanos)
    }
}
