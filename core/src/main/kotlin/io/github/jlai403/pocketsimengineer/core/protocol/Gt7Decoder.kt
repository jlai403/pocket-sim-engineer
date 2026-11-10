package io.github.jlai403.pocketsimengineer.core.protocol

import io.github.jlai403.pocketsimengineer.core.model.Sample

sealed interface DecodeResult {
    data class Success(val sample: Sample) : DecodeResult
    data class Invalid(val reason: String) : DecodeResult
}

fun interface TelemetryDecoder {
    fun decode(datagram: ByteArray, receivedAtNanos: Long): DecodeResult
}

class Gt7Decoder : TelemetryDecoder {
    override fun decode(datagram: ByteArray, receivedAtNanos: Long): DecodeResult {
        val decrypted = Gt7Crypto.decrypt(datagram)
            ?: return DecodeResult.Invalid("decrypt-or-magic")
        if (decrypted.size != Gt7Protocol.PACKET_A_SIZE) {
            return DecodeResult.Invalid("unsupported-size:${decrypted.size}")
        }
        return DecodeResult.Success(PacketAParser.parse(decrypted, receivedAtNanos))
    }
}
