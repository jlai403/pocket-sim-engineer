package io.github.jlai403.lapcraft.core.track

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object TrackSignatureCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): List<TrackSignature> =
        json.decodeFromString<SignatureFile>(text).signatures.map { dto ->
            TrackSignature(
                officialId = dto.officialId,
                name = dto.officialName,
                lengthM = dto.lengthM,
                minX = dto.minX,
                maxX = dto.maxX,
                minZ = dto.minZ,
                maxZ = dto.maxZ,
                path = dto.path.orEmpty().mapNotNull { point ->
                    if (point.size == 2) TrackPoint(point[0], point[1]) else null
                },
                reverseName = dto.reverse?.officialName.orEmpty(),
            )
        }

    @Serializable
    private data class SignatureFile(val signatures: List<SignatureDto> = emptyList())

    @Serializable
    private data class SignatureDto(
        @SerialName("official_id") val officialId: String = "",
        @SerialName("official_name") val officialName: String = "",
        @SerialName("length_m") val lengthM: Double,
        @SerialName("min_x") val minX: Double,
        @SerialName("max_x") val maxX: Double,
        @SerialName("min_z") val minZ: Double,
        @SerialName("max_z") val maxZ: Double,
        @SerialName("path") val path: List<List<Double>>? = null,
        @SerialName("reverse") val reverse: ReverseDto? = null,
    )

    @Serializable
    private data class ReverseDto(
        @SerialName("official_id") val officialId: String = "",
        @SerialName("official_name") val officialName: String = "",
    )
}
