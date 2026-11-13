package io.github.jlai403.lapcraft.core.protocol

object Gt7Protocol {
    val KEY = "Simulator Interface Packet GT7 v".encodeToByteArray()
    const val MAGIC = 0x47375330
    const val PACKET_A_SIZE = 296
    const val HEARTBEAT = "C"
    val PACKET_SIZES = setOf(296, 316, 344, 368)
    const val PORT_RECEIVE = 33740
    const val PORT_SEND = 33739
}

/**
 * GT7 selects the packet format with the heartbeat character. Each format is a strict
 * superset of A, so the base fields keep their offsets; only the length and the Salsa20
 * XOR constant differ.
 */
enum class Gt7PacketFormat(val size: Int, val xor: Int) {
    A(296, 0xDEADBEAF.toInt()),
    B(316, 0xDEADBEEF.toInt()),
    TILDE(344, 0x55FABB4F),
    C(368, 0xDEADBEEF.toInt());

    companion object {
        fun forSize(size: Int): Gt7PacketFormat? = entries.firstOrNull { it.size == size }
    }
}
