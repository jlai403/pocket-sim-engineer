package io.github.jlai403.pocketsimengineer.core.clip

data class TrimRange(val startMs: Long, val endMs: Long)

object TrimPlan {
    /** Null when the clip does not contain the whole lap. */
    fun forSync(sync: ClipSync, sourceDurationMs: Long): TrimRange? {
        if (sync.trimInMs < 0) return null
        if (sync.trimOutMs > sourceDurationMs) return null
        if (sync.trimOutMs <= sync.trimInMs) return null
        return TrimRange(sync.trimInMs, sync.trimOutMs)
    }
}
