package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ProfileNotFoundException : IOException("No GT GridStats profile found for that PSN ID.")

/**
 * Reads the public GT GridStats player page once per user request. It deliberately
 * does not crawl: no pagination, no retries, no background refresh.
 */
class GridStatsProfileClient {
    suspend fun fetch(psnId: String): DriverProfile = withContext(Dispatchers.IO) {
        val trimmed = psnId.trim()
        require(trimmed.isNotEmpty()) { "Enter a PSN ID." }
        val connection = (URL(DriverProfileLinks.gridStats(trimmed)).openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "text/html")
                setRequestProperty("User-Agent", USER_AGENT)
            }
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseGridStatsProfile(html, trimmed)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> throw ProfileNotFoundException()
                else -> throw IOException("GT GridStats returned HTTP $status.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val USER_AGENT =
            "LapCraft/0.1 (+https://github.com/jlai403/lapcraft)"
    }
}

private val NAME_PATTERN = Regex("<h1[^>]*>(.*?)</h1>", RegexOption.DOT_MATCHES_ALL)
private val FLAG_PATTERN = Regex("""flagcdn\.com/w20/([A-Za-z]{2})\.png""")
private val TAG_PATTERN = Regex("<[^>]+>")
private val ENTITY_PATTERN = Regex("&(#x[0-9A-Fa-f]+|#[0-9]+|[A-Za-z]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "apos" to "'",
    "gt" to ">",
    "lt" to "<",
    "quot" to "\"",
)

internal fun parseGridStatsProfile(html: String, psnId: String): DriverProfile {
    val rawName = NAME_PATTERN.find(html)?.groupValues?.getOrNull(1)
        ?: throw IOException("GT GridStats profile page did not include a name.")
    val nickname = unescapeHtml(rawName.replace(TAG_PATTERN, "")).trim()
    if (nickname.isEmpty()) throw IOException("GT GridStats profile page did not include a name.")
    val countryCode = FLAG_PATTERN.find(html)?.groupValues?.getOrNull(1)?.uppercase()
    return DriverProfile(psnId = psnId.trim(), nickname = nickname, countryCode = countryCode)
}

private fun unescapeHtml(text: String): String = ENTITY_PATTERN.replace(text) { match ->
    val entity = match.groupValues[1]
    val decoded = when {
        entity.startsWith("#x", ignoreCase = true) -> numericEntity(entity.drop(2).toIntOrNull(16))
        entity.startsWith("#") -> numericEntity(entity.drop(1).toIntOrNull())
        else -> NAMED_ENTITIES[entity]
    }
    decoded ?: match.value
}

private fun numericEntity(codePoint: Int?): String? =
    codePoint?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) }
