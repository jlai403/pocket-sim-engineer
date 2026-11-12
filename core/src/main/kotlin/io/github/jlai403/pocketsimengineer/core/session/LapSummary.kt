package io.github.jlai403.pocketsimengineer.core.session

import java.time.Instant

/** One row in the flat library. A session holds one lap for new captures; legacy sessions may hold several. */
data class LapSummary(
    val sessionId: String,
    val lapIndex: Int,
    val lapTimeMs: Int,
    val createdAt: String,
    val trackLabel: String,
    val carName: String,
    val hasReplay: Boolean,
    val replayPending: Boolean,
)

enum class LapSort { Newest, Fastest, Track }

data class LapFilter(
    val hasReplay: Boolean? = null,
    val trackLabel: String? = null,
)

fun List<LapSummary>.applyFilter(filter: LapFilter): List<LapSummary> = filter { lap ->
    (filter.hasReplay == null || lap.hasReplay == filter.hasReplay) &&
        (filter.trackLabel == null || lap.trackLabel == filter.trackLabel)
}

fun List<LapSummary>.applySort(sort: LapSort): List<LapSummary> = when (sort) {
    LapSort.Newest -> sortedByDescending { parseInstant(it.createdAt) }
    LapSort.Fastest -> sortedBy { it.lapTimeMs }
    LapSort.Track -> sortedWith(compareBy({ it.trackLabel }, { it.lapTimeMs }))
}

private fun parseInstant(value: String): Instant =
    runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
