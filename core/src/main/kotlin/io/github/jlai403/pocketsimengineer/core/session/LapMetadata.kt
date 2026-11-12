package io.github.jlai403.pocketsimengineer.core.session

import kotlinx.serialization.Serializable

@Serializable
data class LapMetadata(
    val schemaVersion: Int = 1,
    val index: Int,
    val lapTimeMs: Int,
    val startNanos: Long,
    val endNanos: Long,
    val sampleCount: Int,
    val videoFile: String,
    val videoStartOffsetMs: Int,
    val syncOffsetMs: Int = 0,
    val needsCut: Boolean = false,
    /** Imported replay before trimming; null once the lap clip is attached or the import is discarded. */
    val sourceFile: String? = null,
)
