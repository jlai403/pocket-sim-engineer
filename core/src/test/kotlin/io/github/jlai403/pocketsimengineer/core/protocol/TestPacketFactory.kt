package io.github.jlai403.pocketsimengineer.core.protocol

import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TestPacketFactory {
    fun packetA(
        magic: Int = Gt7Protocol.MAGIC,
        speedMps: Float = 42.5f,
        lapCount: Short = 2,
        lastLapTimeMs: Int = 90_000,
        packetId: Int = 7,
        iv1: Int = 0x11223344,
    ): ByteArray {
        val plain = ByteArray(Gt7Protocol.PACKET_A_SIZE)
        val b = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00, magic)
        b.putFloat(0x4C, speedMps)
        b.putInt(0x70, packetId)
        b.putShort(0x74, lapCount)
        b.putInt(0x7C, lastLapTimeMs)

        val iv2 = iv1 xor 0xDEADBEAF.toInt()
        val nonce = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(iv2).putInt(iv1).array()
        val engine = Salsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(Gt7Protocol.KEY), nonce))
        val encrypted = ByteArray(plain.size)
        engine.processBytes(plain, 0, plain.size, encrypted, 0)

        ByteBuffer.wrap(encrypted).order(ByteOrder.LITTLE_ENDIAN).putInt(0x40, iv1)
        return encrypted
    }
}
