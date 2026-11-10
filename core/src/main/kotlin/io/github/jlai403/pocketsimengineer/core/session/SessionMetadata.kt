package io.github.jlai403.pocketsimengineer.core.session

import kotlinx.serialization.Serializable

@Serializable
data class SessionMetadata(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val createdAt: String,
    val trackLabel: String,
    val carCode: Int,
    val carName: String,
    val syncOffsetMs: Int = 0,
    val rawFile: String,
    val rawDeleted: Boolean = false,
    val keepRaw: Boolean = false,
    val laps: List<LapMetadata> = emptyList(),
)
