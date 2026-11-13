package io.github.jlai403.lapcraft.core.track

import io.github.jlai403.lapcraft.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun squarePath(side: Double = 100.0, offset: Double = 0.0): List<TrackPoint> {
        val edge = 20
        val points = ArrayList<TrackPoint>()
        fun line(fromX: Double, fromZ: Double, toX: Double, toZ: Double) {
            for (i in 0..edge) {
                val fraction = i.toDouble() / edge
                points += TrackPoint(
                    offset + fromX + (toX - fromX) * fraction,
                    offset + fromZ + (toZ - fromZ) * fraction,
                )
            }
        }
        line(0.0, 0.0, side, 0.0)
        line(side, 0.0, side, side)
        line(side, side, 0.0, side)
        line(0.0, side, 0.0, 0.0)
        return points
    }

    private fun lap(points: List<TrackPoint>) = TrackSignature(
        lengthM = 400.0,
        minX = 0.0,
        maxX = 100.0,
        minZ = 0.0,
        maxZ = 100.0,
        path = points,
    )

    private fun candidate(
        id: String,
        name: String,
        path: List<TrackPoint>,
        reverseName: String = "",
    ) = TrackSignature(
        officialId = id,
        name = name,
        lengthM = 400.0,
        minX = 0.0,
        maxX = 100.0,
        minZ = 0.0,
        maxZ = 100.0,
        path = path,
        reverseName = reverseName,
    )

    @Test
    fun `square lap has a 400 metre signature`() {
        val signature = TrackIdentifier.signature(squareLap(side = 100.0))!!
        assertEquals(400.0, signature.lengthM, 1.0)
        assertEquals(0.0, signature.minX, 0.01)
        assertEquals(100.0, signature.maxZ, 0.01)
        assertTrue(signature.path.isNotEmpty())
    }

    @Test
    fun `a short sample is not a signature`() {
        assertNull(TrackIdentifier.signature(squareLap(side = 100.0, perEdge = 10)))
    }

    @Test
    fun `exactly one matching candidate is returned`() {
        val match = TrackIdentifier.match(
            lap(emptyList()),
            listOf(
                candidate("a", "A", emptyList()),
                TrackSignature("b", "B", 800.0, 0.0, 200.0, 0.0, 200.0),
            ),
        )
        assertEquals("A", match.name)
    }

    @Test
    fun `two close candidates are ambiguous`() {
        val match = TrackIdentifier.match(
            lap(emptyList()),
            listOf(
                candidate("a", "A", emptyList()),
                TrackSignature("b", "B", 401.0, 0.0, 100.5, 0.0, 100.5),
            ),
        )
        assertNull(match.name)
    }

    @Test
    fun `path coverage picks the circuit the lap actually drove`() {
        val match = TrackIdentifier.match(
            lap(squarePath()),
            listOf(
                candidate("a", "A", squarePath()),
                candidate("b", "B", squarePath(offset = 60.0)),
            ),
        )
        assertEquals("A", match.name)
    }

    @Test
    fun `reverse lap is named after the reverse configuration`() {
        val match = TrackIdentifier.match(
            lap(squarePath().reversed()),
            listOf(candidate("a", "A", squarePath(), reverseName = "A Reverse")),
        )
        assertEquals("A Reverse", match.name)
    }

    @Test
    fun `identical paths offer both names without guessing`() {
        val path = squarePath()
        val match = TrackIdentifier.match(
            lap(path),
            listOf(candidate("a", "A", path), candidate("b", "B", path)),
        )
        assertNull(match.name)
        assertEquals(listOf("A", "B"), match.candidates.map { it.name })
    }
}
