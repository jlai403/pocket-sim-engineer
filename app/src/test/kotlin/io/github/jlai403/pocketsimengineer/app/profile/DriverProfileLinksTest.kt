package io.github.jlai403.pocketsimengineer.app.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverProfileLinksTest {
    @Test
    fun `grid stats link uses the player path`() {
        assertEquals(
            "https://gt-gridstats.com/player/skino2024",
            DriverProfileLinks.gridStats("skino2024"),
        )
    }

    @Test
    fun `gtsh link uses the profile query`() {
        assertEquals(
            "https://gtsh-rank.com/profile/?id=GT_Skino",
            DriverProfileLinks.gtshRank("GT_Skino"),
        )
    }

    @Test
    fun `ids are encoded for a url`() {
        assertEquals(
            "https://gt-gridstats.com/player/a%20b%2Bc",
            DriverProfileLinks.gridStats("a b+c"),
        )
    }
}
