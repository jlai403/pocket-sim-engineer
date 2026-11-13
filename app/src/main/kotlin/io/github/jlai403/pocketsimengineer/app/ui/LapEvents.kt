package io.github.jlai403.pocketsimengineer.app.ui

import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.session.LapMetadata
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

enum class LapEventType { Brake, Throttle, Apex, Exit }

data class LapEvent(val type: LapEventType, val timeMs: Int)

private const val MIN_EVENT_GAP_MS = 600
private const val BRAKE_ON = 1
private const val THROTTLE_ON = 60
private const val TURN_STEERING = 0.35f
private const val TURN_CURVATURE = 0.08f
private const val TURN_WINDOW_MS = 250

/**
 * Onset markers for the lap: one brake and throttle marker when the condition
 * first appears, plus apex / exit for each corner. Repeated markers of the same
 * type are spaced by at least [MIN_EVENT_GAP_MS] so the ruler and map stay readable.
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
    for (sample in window) {
        val timeMs = sample.lapTimeMs(lap)
        val before = previous
        if (sample.brake >= BRAKE_ON && (before == null || before.brake < BRAKE_ON)) {
            add(LapEventType.Brake, timeMs)
        }
        if (sample.throttle >= THROTTLE_ON && (before == null || before.throttle < THROTTLE_ON)) {
            add(LapEventType.Throttle, timeMs)
        }
        previous = sample
    }

    val steeringAvailable = window.any { abs(it.steering) > 0.01f }
    val curvature = if (steeringAvailable) null else curvatureAt(window, lap)
    val threshold = if (steeringAvailable) TURN_STEERING else TURN_CURVATURE
    fun signalAt(index: Int): Float =
        if (steeringAvailable) abs(window[index].steering) else abs(curvature!![index])

    var inCorner = false
    var apexIndex = 0
    var apexValue = 0f
    for (index in window.indices) {
        val value = signalAt(index)
        if (!inCorner) {
            if (value >= threshold) {
                inCorner = true
                apexIndex = index
                apexValue = value
            }
            continue
        }
        if (value > apexValue) {
            apexValue = value
            apexIndex = index
        }
        if (value < threshold) {
            add(LapEventType.Apex, window[apexIndex].lapTimeMs(lap))
            add(LapEventType.Exit, window[index].lapTimeMs(lap))
            inCorner = false
        }
    }
    if (inCorner) {
        add(LapEventType.Apex, window[apexIndex].lapTimeMs(lap))
        add(LapEventType.Exit, window.last().lapTimeMs(lap))
    }
    return events.sortedBy { it.timeMs }
}

/** Heading change over a short window; used for laps recorded before steering was parsed. */
private fun curvatureAt(window: List<Sample>, lap: LapMetadata): FloatArray {
    val times = IntArray(window.size) { window[it].lapTimeMs(lap) }
    fun heading(from: Int, to: Int): Float = atan2(
        (window[to].positionZ - window[from].positionZ).toDouble(),
        (window[to].positionX - window[from].positionX).toDouble(),
    ).toFloat()
    fun indexAt(time: Int): Int {
        val found = times.binarySearch(time)
        return if (found >= 0) found else (-found - 1).coerceIn(0, times.lastIndex)
    }
    return FloatArray(window.size) { index ->
        val before = indexAt(times[index] - TURN_WINDOW_MS)
        val after = indexAt(times[index] + TURN_WINDOW_MS)
        if (before == index || after == index) {
            0f
        } else {
            wrapAngle(heading(index, after) - heading(before, index))
        }
    }
}

private fun wrapAngle(angle: Float): Float {
    val pi = PI.toFloat()
    val twoPi = pi * 2f
    var wrapped = angle
    while (wrapped > pi) wrapped -= twoPi
    while (wrapped < -pi) wrapped += twoPi
    return wrapped
}
