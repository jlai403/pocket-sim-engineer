package io.github.jlai403.lapcraft.core.protocol

import io.github.jlai403.lapcraft.core.model.Sample

sealed interface DecodeResult {
    data class Success(val sample: Sample) : DecodeResult
    data class Invalid(val reason: String) : DecodeResult
}

fun interface TelemetryDecoder {
    fun decode(datagram: ByteArray, receivedAtNanos: Long): DecodeResult
}

class Gt7Decoder : TelemetryDecoder {
    override fun decode(datagram: ByteArray, receivedAtNanos: Long): DecodeResult {
        if (datagram.size !in Gt7Protocol.PACKET_SIZES) {
            return DecodeResult.Invalid("unsupported-size:${datagram.size}")
        }
        val decrypted = Gt7Crypto.decrypt(datagram)
            ?: return DecodeResult.Invalid("decrypt-or-magic")
        return DecodeResult.Success(PacketAParser.parse(decrypted, receivedAtNanos))
    }
}
