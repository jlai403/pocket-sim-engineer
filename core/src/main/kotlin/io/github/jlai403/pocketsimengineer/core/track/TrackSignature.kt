package io.github.jlai403.pocketsimengineer.core.track

/** One point on a racing line, in GT7 world metres. */
data class TrackPoint(val x: Double, val z: Double)

/**
 * A circuit fingerprint: lap length and bounding box, plus the racing line in driving order.
 * The line separates layouts and reverse configurations that share a box and length.
 */
data class TrackSignature(
    val officialId: String = "",
    val name: String = "",
    val lengthM: Double,
    val minX: Double,
    val maxX: Double,
    val minZ: Double,
    val maxZ: Double,
    val path: List<TrackPoint> = emptyList(),
    val reverseName: String = "",
)

/** A track the lap plausibly drove, with the share of lap positions near its line. */
data class TrackCandidate(val name: String, val officialId: String, val coverage: Float)

/** The automatic answer and the candidates worth offering the user. */
data class TrackMatch(val name: String?, val candidates: List<TrackCandidate>)
