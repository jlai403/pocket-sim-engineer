package io.github.jlai403.lapcraft.core.clip

import kotlin.math.abs

/** Maps a lap-relative telemetry time to video time using one frame anchor at the lap end. */
data class ClipSync(val videoAnchorMs: Long, val lapTimeMs: Int) {
    val trimInMs: Long get() = videoAnchorMs - lapTimeMs
    val trimOutMs: Long get() = videoAnchorMs

    fun telemetryMsAt(videoMs: Long): Int =
        (lapTimeMs - (videoAnchorMs - videoMs)).toInt().coerceIn(0, lapTimeMs)

    fun videoMsAt(telemetryMs: Int): Long =
        videoAnchorMs - (lapTimeMs - telemetryMs)
}

sealed interface SyncCheck {
    data object Ok : SyncCheck
    data class ClipTooShort(val missingMs: Long) : SyncCheck
    data class StartNotAtLine(val trackPercent: Float) : SyncCheck
}

object SyncValidator {
    fun check(
        sync: ClipSync,
        sourceDurationMs: Long,
        startTrackPercent: Float,
        tolerancePercent: Float = 2f,
    ): SyncCheck {
        if (sync.trimInMs < 0) return SyncCheck.ClipTooShort(-sync.trimInMs)
        if (sync.trimOutMs > sourceDurationMs) return SyncCheck.ClipTooShort(sync.trimOutMs - sourceDurationMs)
        if (abs(startTrackPercent) > tolerancePercent &&
            abs(startTrackPercent - 100f) > tolerancePercent
        ) {
            return SyncCheck.StartNotAtLine(startTrackPercent)
        }
        return SyncCheck.Ok
    }
}
