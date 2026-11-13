package io.github.jlai403.lapcraft.core.session

import kotlinx.serialization.SerialName
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
    /** Clip time before the start frame, kept when the keyframe cut lands early. */
    @SerialName("videoStartOffsetMs")
    val leadInMs: Int,
    val syncOffsetMs: Int = 0,
    val needsCut: Boolean = false,
    /** Imported replay before trimming; null once the lap clip is attached or the import is discarded. */
    val sourceFile: String? = null,
    /** PSN ID of the driver for this lap/replay; null for legacy laps. */
    val driverPsnId: String? = null,
)
