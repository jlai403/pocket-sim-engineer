package io.github.jlai403.lapcraft.core.session

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SessionJsonCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSession(metadata: SessionMetadata): String = json.encodeToString(metadata)

    fun decodeSession(text: String): SessionMetadata = json.decodeFromString(text)

    fun encodeLap(lap: LapMetadata): String = json.encodeToString(lap)

    fun decodeLap(text: String): LapMetadata = json.decodeFromString(text)
}
