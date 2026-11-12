package io.github.jlai403.pocketsimengineer.app.track

import android.content.Context
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.track.TrackIdentifier
import io.github.jlai403.pocketsimengineer.core.track.TrackSignatureCodec

/** Loads the bundled CC0 signatures and names a lap from its coordinates. */
class TrackCatalog(context: Context) {
    private val signatures by lazy {
        context.assets.open(ASSET).bufferedReader().use { reader ->
            TrackSignatureCodec.decode(reader.readText())
        }
    }

    fun identify(samples: List<Sample>): String? {
        val signature = TrackIdentifier.signature(samples) ?: return null
        return TrackIdentifier.match(signature, signatures)?.name?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val ASSET = "track-signatures.json"
    }
}
