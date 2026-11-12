package io.github.jlai403.pocketsimengineer.app.profile

/** Regional-indicator pair for a two-letter ISO country code, or null when unknown. */
fun countryFlagEmoji(countryCode: String?): String? {
    val code = countryCode?.trim()?.uppercase() ?: return null
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
