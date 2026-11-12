package io.github.jlai403.pocketsimengineer.core.protocol

/** Remembers the first distinct decode failures so diagnostics can name the packet format. */
class DecodeFailureLog(private val limit: Int = 5) {
    private val entries = ArrayDeque<String>()

    /** Returns true when this failure is new and should be logged. */
    @Synchronized
    fun record(size: Int, reason: String): Boolean {
        val line = "$size $reason"
        if (entries.contains(line)) return false
        if (entries.size >= limit) return false
        entries.addLast(line)
        return true
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()
}
