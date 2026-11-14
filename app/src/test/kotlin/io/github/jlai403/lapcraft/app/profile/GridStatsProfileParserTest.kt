package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class GridStatsProfileParserTest {
    @Test
    fun `parses the nickname and country flag`() {
        val html = """
            <h1 class="name">Skino &amp; Co</h1>
            <p>skino2024</p>
            <img src="https://flagcdn.com/w20/gb.png" alt="GB">
        """.trimIndent()

        assertEquals(
            DriverProfile("skino2024", "Skino & Co", "GB"),
            parseGridStatsProfile(html, "skino2024"),
        )
    }

    @Test
    fun `strips markup inside the name`() {
        val html = "<h1><span>Skino</span></h1>"

        assertEquals("Skino", parseGridStatsProfile(html, "skino2024").nickname)
    }

    @Test
    fun `a missing flag leaves the country empty`() {
        val html = "<h1>Skino</h1>"

        assertNull(parseGridStatsProfile(html, "skino2024").countryCode)
    }

    @Test
    fun `a page without a name is rejected`() {
        assertThrows(IOException::class.java) {
            parseGridStatsProfile("<html><body>No profile</body></html>", "skino2024")
        }
    }
}
