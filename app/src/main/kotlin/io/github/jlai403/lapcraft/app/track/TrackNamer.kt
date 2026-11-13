package io.github.jlai403.lapcraft.app.track

import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.SessionStore
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Names laps captured before track identification existed. */
class TrackNamer(
    private val store: SessionStore,
    private val catalog: TrackCatalog,
) {
    suspend fun identifyUnknownLaps(): Int = withContext(Dispatchers.IO) {
        var renamed = 0
        for (session in store.list()) {
            if (session.trackLabel != UNKNOWN) continue
            val samples = session.laps.minByOrNull { it.lapTimeMs }
                ?.let { lap -> lapSamples(session.sessionId, lap.index) }
                ?: continue
            val name = catalog.identify(samples) ?: continue
            store.rename(session.sessionId, name)
            Diagnostics.log("track: backfilled ${session.sessionId} -> $name")
            renamed++
        }
        renamed
    }

    fun candidates(sessionId: String, lapIndex: Int): List<TrackCandidate> =
        catalog.candidates(lapSamples(sessionId, lapIndex))

    private fun lapSamples(sessionId: String, lapIndex: Int): List<Sample> {
        val lap = store.lap(sessionId, lapIndex) ?: return emptyList()
        val samples = runCatching { store.readSamples(sessionId, lapIndex) }.getOrDefault(emptyList())
        return samples.filter { it.timeNanos in lap.startNanos..lap.endNanos }
    }

    companion object {
        const val UNKNOWN = "Unknown"

        /** Bumped when the matcher or bundled signatures change, so old laps are retried once. */
        const val VERSION = 1
    }
}
