package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.model.Sample
import java.util.Locale

object CsvExporter {
    private const val HEADER =
        "timeMs,speedMps,speedKph,throttle,brake,gear,rpm,positionX,positionY,positionZ," +
            "lapCount,lastLapTimeMs,bestLapTimeMs,flags,carCode,fuel," +
            "tyreTempFL,tyreTempFR,tyreTempRL,tyreTempRR,steering,currentLapTimeMs"

    fun export(samples: List<Sample>): String {
        val first = samples.firstOrNull()
        val out = StringBuilder(HEADER).append('\n')
        for (sample in samples) {
            val timeMs = if (first == null) 0 else (sample.timeNanos - first.timeNanos) / 1_000_000
            out.append(timeMs).append(',')
            out.append(fmt(sample.speedMps)).append(',')
            out.append(fmt(sample.speedMps * 3.6f)).append(',')
            out.append(sample.throttle).append(',')
            out.append(sample.brake).append(',')
            out.append(sample.gear).append(',')
            out.append(fmt(sample.rpm)).append(',')
            out.append(fmt(sample.positionX)).append(',')
            out.append(fmt(sample.positionY)).append(',')
            out.append(fmt(sample.positionZ)).append(',')
            out.append(sample.lapCount).append(',')
            out.append(sample.lastLapTimeMs).append(',')
            out.append(sample.bestLapTimeMs).append(',')
            out.append(sample.flags).append(',')
            out.append(sample.carCode).append(',')
            out.append(fmt(sample.fuel)).append(',')
            out.append(fmt(sample.tyreTempFL)).append(',')
            out.append(fmt(sample.tyreTempFR)).append(',')
            out.append(fmt(sample.tyreTempRL)).append(',')
            out.append(fmt(sample.tyreTempRR)).append(',')
            out.append(fmt(sample.steering)).append(',')
            out.append(sample.currentLapTimeMs).append('\n')
        }
        return out.toString()
    }

    private fun fmt(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
}
