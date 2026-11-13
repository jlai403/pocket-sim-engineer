package io.github.jlai403.lapcraft.core.session

import kotlinx.serialization.Serializable

@Serializable
data class SessionMetadata(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val createdAt: String,
    val trackLabel: String,
    /** `System.nanoTime()` immediately before the first captured frame; 0 for older sessions. */
    val recordingStartNanos: Long = 0,
    val carCode: Int,
    val carName: String,
    val syncOffsetMs: Int = 0,
    val rawFile: String,
    val audioFile: String? = null,
    val rawDeleted: Boolean = false,
    val keepRaw: Boolean = false,
    val laps: List<LapMetadata> = emptyList(),
)
