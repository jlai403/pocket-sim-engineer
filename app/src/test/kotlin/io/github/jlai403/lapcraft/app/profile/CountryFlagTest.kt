package io.github.jlai403.lapcraft.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryFlagTest {
    @Test
    fun `renders a two letter code as a flag`() {
        assertEquals("🇬🇧", countryFlagEmoji("GB"))
        assertEquals("🇺🇸", countryFlagEmoji("us"))
    }

    @Test
    fun `unknown codes have no flag`() {
        assertNull(countryFlagEmoji(null))
        assertNull(countryFlagEmoji(""))
        assertNull(countryFlagEmoji("USA"))
        assertNull(countryFlagEmoji("1B"))
        assertNull(countryFlagEmoji("G B"))
    }
}
