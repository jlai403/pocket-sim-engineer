package io.github.jlai403.lapcraft.app.track

import android.content.Context
import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.track.TrackCandidate
import io.github.jlai403.lapcraft.core.track.TrackIdentifier
import io.github.jlai403.lapcraft.core.track.TrackMatch
import io.github.jlai403.lapcraft.core.track.TrackSignatureCodec

/** Loads the bundled CC0 signatures and names a lap from its coordinates. */
class TrackCatalog(context: Context) {
    private val signatures by lazy {
        runCatching {
            context.assets.open(ASSET).bufferedReader().use { reader ->
                TrackSignatureCodec.decode(reader.readText())
            }
        }.onFailure {
            Diagnostics.log("track: signature asset failed: ${it.message}")
        }.getOrDefault(emptyList())
    }

    fun identify(samples: List<Sample>): String? = analyze(samples).name

    fun candidates(samples: List<Sample>): List<TrackCandidate> = analyze(samples).candidates

    private fun analyze(samples: List<Sample>): TrackMatch {
        val signature = TrackIdentifier.signature(samples)
        val match = if (signature == null) {
            TrackMatch(name = null, candidates = emptyList())
        } else {
            TrackIdentifier.match(signature, signatures)
        }
        Diagnostics.log(
            "track: %d samples%s -> %s".format(
                samples.size,
                signature?.let {
                    " · len=%.0f · box=%.0f..%.0f / %.0f..%.0f".format(
                        it.lengthM,
                        it.minX,
                        it.maxX,
                        it.minZ,
                        it.maxZ,
                    )
                } ?: "",
                match.name ?: match.candidates.joinToString { it.name }.ifBlank { "unknown" },
            ),
        )
        return match
    }

    private companion object {
        const val ASSET = "track-signatures.json"
    }
}
