package io.github.jlai403.lapcraft.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Gt7DecoderTest {
    @Test
    fun `all four formats decode to a sample`() {
        Gt7PacketFormat.entries.forEach { format ->
            val result = Gt7Decoder().decode(
                TestPacketFactory.packet(format = format, lapCount = 4),
                receivedAtNanos = 1_000,
            )
            assertTrue(result is DecodeResult.Success, "$format failed: $result")
            assertEquals(4, (result as DecodeResult.Success).sample.lapCount)
        }
    }

    @Test
    fun `unknown size is rejected with its size in the reason`() {
        val result = Gt7Decoder().decode(ByteArray(123), 1_000)
        assertEquals(DecodeResult.Invalid("unsupported-size:123"), result)
    }
}
