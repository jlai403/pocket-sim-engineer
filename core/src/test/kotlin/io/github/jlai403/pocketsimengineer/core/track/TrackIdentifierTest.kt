package io.github.jlai403.pocketsimengineer.core.track

import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackIdentifierTest {
    private fun sample(x: Double, z: Double, timeNanos: Long = 0) = Sample(
        timeNanos = timeNanos,
        speedMps = 0f,
        throttle = 0,
        brake = 0,
        gear = 1,
        rpm = 0f,
        positionX = x.toFloat(),
        positionY = 0f,
        positionZ = z.toFloat(),
        lapCount = 1,
        bestLapTimeMs = 0,
        lastLapTimeMs = 0,
        flags = 0,
        carCode = 0,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = 0,
    )

    private fun squareLap(side: Double, perEdge: Int = 20): List<Sample> {
        val points = ArrayList<Sample>()
        var t = 0L
        fun edge(fromX: Double, fromZ: Double, toX: Double, toZ: Double) {
            for (i in 0..perEdge) {
                val fraction = i.toDouble() / perEdge
                points += sample(
                    fromX + (toX - fromX) * fraction,
                    fromZ + (toZ - fromZ) * fraction,
                    t++ * 1_000_000,
                )
            }
        }
        edge(0.0, 0.0, side, 0.0)
        edge(side, 0.0, side, side)
        edge(side, side, 0.0, side)
        edge(0.0, side, 0.0, 0.0)
        return points
    }

    @Test
    fun `square lap has a 400 metre signature`() {
        val signature = TrackIdentifier.signature(squareLap(side = 100.0))!!
        assertEquals(400.0, signature.lengthM, 1.0)
        assertEquals(0.0, signature.minX, 0.01)
        assertEquals(100.0, signature.maxZ, 0.01)
    }

    @Test
    fun `a short sample is not a signature`() {
        assertNull(TrackIdentifier.signature(squareLap(side = 100.0, perEdge = 10)))
    }

    @Test
    fun `exactly one matching candidate is returned`() {
        val lap = TrackSignature("", "", 400.0, 0.0, 100.0, 0.0, 100.0)
        val match = TrackIdentifier.match(
            lap,
            listOf(
                TrackSignature("a", "A", 405.0, 0.0, 101.0, 0.0, 101.0),
                TrackSignature("b", "B", 800.0, 0.0, 200.0, 0.0, 200.0),
            ),
        )
        assertEquals("A", match?.name)
    }

    @Test
    fun `two close candidates are ambiguous`() {
        val lap = TrackSignature("", "", 400.0, 0.0, 100.0, 0.0, 100.0)
        assertNull(
            TrackIdentifier.match(
                lap,
                listOf(
                    TrackSignature("a", "A", 400.0, 0.0, 100.0, 0.0, 100.0),
                    TrackSignature("b", "B", 401.0, 0.0, 100.5, 0.0, 100.5),
                ),
            ),
        )
    }
}
