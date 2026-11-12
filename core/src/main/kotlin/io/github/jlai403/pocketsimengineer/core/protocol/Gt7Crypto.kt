package io.github.jlai403.pocketsimengineer.core.protocol

import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Gt7Crypto {
    fun decrypt(datagram: ByteArray): ByteArray? {
        if (datagram.size < 68) return null

        // The XOR constant is selected by the packet format. Try the matching format first,
        // then the rest: the console may still be answering an earlier heartbeat.
        val preferred = Gt7PacketFormat.forSize(datagram.size)
        val candidates = if (preferred == null) {
            Gt7PacketFormat.entries
        } else {
            listOf(preferred) + Gt7PacketFormat.entries.filter { it != preferred }
        }
        for (format in candidates) {
            val decrypted = decryptWith(datagram, format.xor)
            if (decrypted != null) return decrypted
        }
        return null
    }

    private fun decryptWith(datagram: ByteArray, xor: Int): ByteArray? {
        val buffer = ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN)
        val iv1 = buffer.getInt(0x40)
        val iv2 = iv1 xor xor
        val nonce = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(iv2)
            .putInt(iv1)
            .array()

        val engine = Salsa20Engine()
        engine.init(false, ParametersWithIV(KeyParameter(Gt7Protocol.KEY), nonce))
        val decrypted = ByteArray(datagram.size)
        engine.processBytes(datagram, 0, datagram.size, decrypted, 0)

        val magic = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        return if (magic == Gt7Protocol.MAGIC) decrypted else null
    }
}
