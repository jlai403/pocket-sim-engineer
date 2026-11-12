package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.abs

enum class LapEventType { Brake, Coast, Throttle, Turn }

data class LapEvent(val type: LapEventType, val timeMs: Int)

private const val MIN_EVENT_GAP_MS = 600
private const val BRAKE_ON = 60
private const val THROTTLE_ON = 60
private const val COAST_LIMIT = 20
private const val TURN_STEERING = 0.35f

/**
 * Onset markers for the lap: one brake, throttle, and coast marker when the
 * condition first appears, plus steering peaks for turns. Repeated markers of
 * the same type are spaced by at least [MIN_EVENT_GAP_MS] so the ruler and map
 * stay readable.
 */
internal fun lapEvents(window: List<Sample>, lap: LapMetadata): List<LapEvent> {
    if (window.isEmpty()) return emptyList()
    val events = mutableListOf<LapEvent>()
    val lastTime = mutableMapOf<LapEventType, Int>()

    fun add(type: LapEventType, timeMs: Int) {
        val previous = lastTime[type]
        if (previous != null && timeMs - previous < MIN_EVENT_GAP_MS) return
        lastTime[type] = timeMs
        events += LapEvent(type, timeMs)
    }

    var previous: Sample? = null
    for ((index, sample) in window.withIndex()) {
        val timeMs = sample.lapTimeMs(lap)
        val before = previous
        if (sample.brake >= BRAKE_ON && (before == null || before.brake < BRAKE_ON)) {
            add(LapEventType.Brake, timeMs)
        }
        if (sample.throttle >= THROTTLE_ON && (before == null || before.throttle < THROTTLE_ON)) {
            add(LapEventType.Throttle, timeMs)
        }
        val coasting = sample.brake < COAST_LIMIT &&
            sample.throttle < COAST_LIMIT &&
            sample.speedMps > 3f
        val wasCoasting = before != null &&
            before.brake < COAST_LIMIT &&
            before.throttle < COAST_LIMIT &&
            before.speedMps > 3f
        if (coasting && !wasCoasting) add(LapEventType.Coast, timeMs)

        val steering = abs(sample.steering)
        val steeringBefore = if (index > 0) abs(window[index - 1].steering) else 0f
        val steeringAfter = if (index + 1 < window.size) abs(window[index + 1].steering) else 0f
        if (steering >= TURN_STEERING && steering >= steeringBefore && steering > steeringAfter) {
            add(LapEventType.Turn, timeMs)
        }
        previous = sample
    }
    return events.sortedBy { it.timeMs }
}
