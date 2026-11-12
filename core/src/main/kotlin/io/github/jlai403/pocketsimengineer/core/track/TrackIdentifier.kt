package io.github.jlai403.pocketsimengineer.core.track

import io.github.jlai403.pocketsimengineer.core.model.Sample
import kotlin.math.abs
import kotlin.math.hypot

object TrackIdentifier {
    const val MIN_SAMPLES = 60
    const val MAX_STEP_M = 200.0
    const val LENGTH_TOLERANCE = 0.04
    const val CENTRE_TOLERANCE_M = 120.0
    const val EXTENT_TOLERANCE = 0.20

    /** Returns null for a partial lap or a path with no usable movement. */
    fun signature(samples: List<Sample>): TrackSignature? {
        if (samples.size < MIN_SAMPLES) return null

        var length = 0.0
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE

        samples.forEachIndexed { index, sample ->
            val x = sample.positionX.toDouble()
            val z = sample.positionZ.toDouble()
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minZ = minOf(minZ, z)
            maxZ = maxOf(maxZ, z)
            if (index > 0) {
                val previous = samples[index - 1]
                val step = hypot(
                    x - previous.positionX.toDouble(),
                    z - previous.positionZ.toDouble(),
                )
                if (step <= MAX_STEP_M) length += step
            }
        }

        if (length <= 0.0) return null
        return TrackSignature(
            lengthM = length,
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
        )
    }

    /** Returns the only candidate that matches, or null when none or several do. */
    fun match(lap: TrackSignature, candidates: List<TrackSignature>): TrackSignature? =
        candidates.filter { candidate -> matches(lap, candidate) }.singleOrNull()

    private fun matches(lap: TrackSignature, candidate: TrackSignature): Boolean {
        if (candidate.lengthM <= 0.0) return false
        if (abs(lap.lengthM - candidate.lengthM) / candidate.lengthM > LENGTH_TOLERANCE) return false
        if (abs(centreX(lap) - centreX(candidate)) > CENTRE_TOLERANCE_M) return false
        if (abs(centreZ(lap) - centreZ(candidate)) > CENTRE_TOLERANCE_M) return false
        return withinExtent(extentX(lap), extentX(candidate)) &&
            withinExtent(extentZ(lap), extentZ(candidate))
    }

    private fun centreX(signature: TrackSignature): Double = (signature.minX + signature.maxX) / 2
    private fun centreZ(signature: TrackSignature): Double = (signature.minZ + signature.maxZ) / 2
    private fun extentX(signature: TrackSignature): Double = signature.maxX - signature.minX
    private fun extentZ(signature: TrackSignature): Double = signature.maxZ - signature.minZ

    private fun withinExtent(lap: Double, candidate: Double): Boolean {
        if (candidate <= 0.0) return false
        return abs(lap - candidate) / candidate <= EXTENT_TOLERANCE
    }
}
