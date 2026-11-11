package io.github.jlai403.pocketsimengineer.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Ipv4Test {
    @Test
    fun `accepts and trims a dotted quad`() {
        assertEquals("192.168.1.50", Ipv4.normalize(" 192.168.1.50 "))
        assertEquals("10.0.0.1", Ipv4.normalize("10.0.0.1"))
    }

    @Test
    fun `canonicalizes leading zeros`() {
        assertEquals("192.168.1.5", Ipv4.normalize("192.168.001.005"))
    }

    @Test
    fun `rejects partial and out-of-range addresses`() {
        assertNull(Ipv4.normalize(""))
        assertNull(Ipv4.normalize("192.168.1"))
        assertNull(Ipv4.normalize("192.168.1.256"))
        assertNull(Ipv4.normalize("192.168.1.-1"))
        assertNull(Ipv4.normalize("ps5.local"))
        assertNull(Ipv4.normalize("192.168.1.1.1"))
        assertNull(Ipv4.normalize("192.168.1."))
        assertNull(Ipv4.normalize("192.168.1.1a"))
    }
}
