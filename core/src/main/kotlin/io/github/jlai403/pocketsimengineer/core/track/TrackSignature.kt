package io.github.jlai403.pocketsimengineer.core.track

/** A circuit fingerprint from one completed lap: five numbers that are stable per track. */
data class TrackSignature(
    val officialId: String = "",
    val name: String = "",
    val lengthM: Double,
    val minX: Double,
    val maxX: Double,
    val minZ: Double,
    val maxZ: Double,
)
