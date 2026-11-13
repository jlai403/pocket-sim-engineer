package io.github.jlai403.lapcraft.app.profile

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Public profile pages; the app only opens these in the browser. */
object DriverProfileLinks {
    fun gridStats(psnId: String): String = "https://gt-gridstats.com/player/${encode(psnId)}"

    fun gtshRank(psnId: String): String = "https://gtsh-rank.com/profile/?id=${encode(psnId)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
