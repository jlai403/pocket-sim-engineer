package io.github.jlai403.lapcraft.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Gt7ProtocolTest {
    @Test
    fun `protocol constants match the GT7 wire format`() {
        assertEquals(0x47375330, Gt7Protocol.MAGIC)
        assertEquals(33740, Gt7Protocol.PORT_RECEIVE)
        assertEquals(33739, Gt7Protocol.PORT_SEND)
        assertEquals(296, Gt7Protocol.PACKET_A_SIZE)
        assertEquals(32, Gt7Protocol.KEY.size)
        assertEquals("Simulator Interface Packet GT7 v", Gt7Protocol.KEY.decodeToString())
    }
}
