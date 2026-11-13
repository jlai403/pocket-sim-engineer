package io.github.jlai403.lapcraft.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apk: String = "LapCraft-debug.apk",
    val sha256: String = "",
    val notes: String = "",
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}

object UpdateJsonCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): UpdateManifest? =
        runCatching { json.decodeFromString<UpdateManifest>(text) }.getOrNull()

    fun encode(manifest: UpdateManifest): String = json.encodeToString(manifest)
}
