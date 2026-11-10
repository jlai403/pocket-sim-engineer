package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsvExporterTest {
    private fun sample(timeNanos: Long, speedMps: Float, throttle: Int = 200) = Sample(
        timeNanos = timeNanos,
        speedMps = speedMps,
        throttle = throttle,
        brake = 10,
        gear = 4,
        rpm = 7000f,
        positionX = 1f,
        positionY = 2f,
        positionZ = 3f,
        lapCount = 2,
        bestLapTimeMs = 88_000,
        lastLapTimeMs = 90_000,
        flags = 1,
        carCode = 3474,
        fuel = 45.5f,
        tyreTempFL = 80f,
        tyreTempFR = 81f,
        tyreTempRL = 82f,
        tyreTempRR = 83f,
        packetId = 99,
        steering = 0.25f,
        currentLapTimeMs = 12_345,
    )

    @Test
    fun `writes a header and one row per sample`() {
        val csv = CsvExporter.export(
            listOf(
                sample(0L, 10f),
                sample(1_666_666_667L, 20f, throttle = 100),
            ),
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("timeMs,"))
        assertTrue(lines[0].contains("speedMps"))
        assertTrue(lines[0].contains("throttle"))
        assertTrue(lines[0].contains("brake"))
        assertTrue(lines[0].contains("gear"))
        assertTrue(lines[0].contains("rpm"))
    }

    @Test
    fun `exports relative milliseconds and speeds`() {
        val csv = CsvExporter.export(
            listOf(
                sample(5_000_000_000L, 10f),
                sample(6_500_000_000L, 20f),
            ),
        )
        val rows = csv.trim().lines().drop(1)
        assertTrue(rows[0].startsWith("0,"))
        assertTrue(rows[1].startsWith("1500,"))
        assertTrue(rows[0].contains("36.00"))
        assertTrue(rows[1].contains("72.00"))
    }

    @Test
    fun `handles an empty lap`() {
        val csv = CsvExporter.export(emptyList())
        assertEquals(1, csv.trim().lines().size)
    }
}
