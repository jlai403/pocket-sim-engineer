package io.github.jlai403.pocketsimengineer.core.network

object Ipv4 {
    /** Returns a canonical dotted-quad address, or null when [raw] is not a valid IPv4 literal. */
    fun normalize(raw: String): String? {
        val parts = raw.trim().split('.')
        if (parts.size != 4) return null
        val octets = ArrayList<Int>(4)
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return null
            if (part.any { !it.isDigit() }) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets += value
        }
        return octets.joinToString(".")
    }
}
