package io.github.jlai403.pocketsimengineer.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Gt7CryptoTest {
    @Test
    fun `decrypts a valid packet`() {
        val encrypted = TestPacketFactory.packetA(speedMps = 42.5f)
        val plain = Gt7Crypto.decrypt(encrypted)!!
        assertEquals(Gt7Protocol.MAGIC, ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(42.5f, ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).getFloat(0x4C))
    }

    @Test
    fun `rejects a short datagram`() {
        assertNull(Gt7Crypto.decrypt(ByteArray(10)))
    }

    @Test
    fun `rejects a bad magic`() {
        val encrypted = TestPacketFactory.packetA(magic = 0x12345678)
        assertNull(Gt7Crypto.decrypt(encrypted))
    }

    @Test
    fun `packet C decrypts with the BEEF constant`() {
        val packet = TestPacketFactory.packet(format = Gt7PacketFormat.C, lapCount = 3)
        val decrypted = Gt7Crypto.decrypt(packet)
        assertNotNull(decrypted)
        assertEquals(368, decrypted!!.size)
    }

    @Test
    fun `packet A still decrypts with the BEAF constant`() {
        val packet = TestPacketFactory.packet(format = Gt7PacketFormat.A, lapCount = 3)
        assertNotNull(Gt7Crypto.decrypt(packet))
    }
}
