package io.github.jlai403.lapcraft.core.clip

import io.github.jlai403.lapcraft.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class TrackAlignmentTest {
    private fun sample(x: Double, z: Double, timeMs: Long) = Sample(
        timeNanos = timeMs * 1_000_000L,
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

    /** Samples one metre apart along a polyline, one millisecond per metre. */
    private fun lapAlong(points: List<Pair<Double, Double>>, stepM: Double = 1.0): List<Sample> {
        val lengths = (1 until points.size).map { index ->
            hypot(points[index].first - points[index - 1].first, points[index].second - points[index - 1].second)
        }
        val total = lengths.sum()
        val out = ArrayList<Sample>()
        out += sample(points[0].first, points[0].second, 0)
        var next = stepM
        while (next <= total) {
            var segment = 0
            var base = 0.0
            while (base + lengths[segment] < next) {
                base += lengths[segment]
                segment++
            }
            val t = (next - base) / lengths[segment]
            val start = points[segment]
            val end = points[segment + 1]
            out += sample(
                start.first + (end.first - start.first) * t,
                start.second + (end.second - start.second) * t,
                next.roundToInt().toLong(),
            )
            next += stepM
        }
        return out
    }

    private fun line(fromX: Double, toX: Double, z: Double) =
        lapAlong(listOf(fromX to z, toX to z))

    @Test
    fun `a parallel line keeps every sample on the same axis`() {
        val primary = line(0.0, 1_000.0, z = 0.0)
        val reference = line(0.0, 1_000.0, z = 4.0)

        val alignment = TrackProgress.align(primary, reference, 0)!!

        reference.indices.forEach { index ->
            val expected = reference[index].positionX / 10f
            val actual = alignment.axisPercentOfSample(index)
            assertTrue(abs(actual - expected) <= 0.5f, "index=$index expected=$expected actual=$actual")
        }
    }

    @Test
    fun `a longer line lands on the place, not on its own distance`() {
        val primary = line(0.0, 1_000.0, z = 0.0)
        val detour = listOf(0.0 to 0.0, 400.0 to 0.0, 600.0 to 200.0, 800.0 to 0.0, 1_000.0 to 0.0)
        val reference = lapAlong(detour)
        val lapTimeMs = 1_166

        val alignment = TrackProgress.align(primary, reference, 0)!!
        val index = reference.indexOfFirst { it.positionX >= 200f }
        val aligned = alignment.axisPercentOfSample(index)
        val ownDistance = TrackProgress.percentAt(reference, 0, (reference[index].timeNanos / 1_000_000L).toInt())

        assertTrue(abs(aligned - 20f) <= 1f, "aligned=$aligned")
        assertTrue(abs(ownDistance - aligned) > 2f, "ownDistance=$ownDistance aligned=$aligned")
        assertEquals(
            (reference[index].timeNanos / 1_000_000L).toInt(),
            alignment.referenceTimeAt(20f, lapTimeMs),
        )
    }

    @Test
    fun `the axis follows a crossing track without jumping`() {
        val points = (0..1_000).map { step ->
            val t = step / 1_000.0 * 2 * PI
            100 * sin(t) to 100 * sin(2 * t)
        }
        val lap = lapAlong(points)

        val alignment = TrackProgress.align(lap, lap, 0)!!

        var previous = 0f
        lap.indices.forEach { index ->
            val actual = alignment.axisPercentOfSample(index)
            assertTrue(actual >= previous, "axis went backwards at $index")
            assertTrue(actual - previous < 5f, "axis jumped ${actual - previous} at $index")
            previous = actual
        }
        assertEquals(100f, previous, 0.5f)
    }

    @Test
    fun `a spin holds the axis`() {
        val primary = line(0.0, 1_000.0, z = 0.0)
        val reference = listOf(
            sample(0.0, 0.0, 0),
            sample(250.0, 0.0, 250),
            sample(500.0, 0.0, 500),
            sample(480.0, 0.0, 516),
            sample(400.0, 0.0, 532),
            sample(520.0, 0.0, 548),
            sample(750.0, 0.0, 764),
            sample(1_000.0, 0.0, 1_014),
        )

        val alignment = TrackProgress.align(primary, reference, 0)!!

        var previous = 0f
        reference.indices.forEach { index ->
            val actual = alignment.axisPercentOfSample(index)
            assertTrue(actual >= previous, "axis went backwards at $index")
            previous = actual
        }
        assertEquals(100f, previous, 0.5f)
    }

    @Test
    fun `a teleport is followed to the closest place`() {
        val primary = line(0.0, 1_000.0, z = 0.0)
        val reference = listOf(
            sample(0.0, 0.0, 0),
            sample(250.0, 0.0, 250),
            sample(500.0, 0.0, 500),
            sample(900.0, 0.0, 516),
            sample(1_000.0, 0.0, 616),
        )

        val alignment = TrackProgress.align(primary, reference, 0)!!

        assertEquals(25f, alignment.axisPercentOfSample(1), 1f)
        assertEquals(90f, alignment.axisPercentOfSample(3), 1f)
        assertEquals(100f, alignment.axisPercentOfSample(4), 0.5f)
    }

    @Test
    fun `a primary path with a teleport cannot align`() {
        val primary = listOf(
            sample(0.0, 0.0, 0),
            sample(200.0, 0.0, 200),
            sample(5_000.0, 0.0, 216),
            sample(5_200.0, 0.0, 416),
        )
        val reference = line(0.0, 1_000.0, z = 0.0)

        assertNull(TrackProgress.align(primary, reference, 0))
    }

    @Test
    fun `a lap without positions cannot align`() {
        val primary = line(0.0, 1_000.0, z = 0.0)
        val stationary = listOf(sample(10.0, 10.0, 0), sample(10.0, 10.0, 16))

        assertNull(TrackProgress.align(stationary, primary, 0))
        assertNull(TrackProgress.align(primary, emptyList(), 0))
    }

    @Test
    fun `reference time clamps to the lap ends`() {
        val primary = line(0.0, 1_000.0, z = 0.0)
        val reference = line(0.0, 1_000.0, z = 4.0)

        val alignment = TrackProgress.align(primary, reference, 0)!!

        assertEquals(0, alignment.referenceTimeAt(-5f, 1_000))
        assertEquals(1_000, alignment.referenceTimeAt(105f, 1_000))
        assertEquals(500f, alignment.referenceTimeAt(50f, 1_000).toFloat(), 5f)
    }

    @Test
    fun `axis percent matches the chart axis for the same lap`() {
        val points = (0..720).map { step ->
            val t = step / 720.0 * 2 * PI
            200 * cos(t) to 120 * sin(t)
        }
        val lap = lapAlong(points)
        val table = TrackProgress.table(lap, 0)!!

        val alignment = TrackProgress.align(lap, lap, 0)!!

        lap.indices.forEach { index ->
            val charted = table.percentOfSample(index)
            val aligned = alignment.axisPercentOfSample(index)
            assertTrue(abs(charted - aligned) <= 1f, "index=$index charted=$charted aligned=$aligned")
        }
    }
}
