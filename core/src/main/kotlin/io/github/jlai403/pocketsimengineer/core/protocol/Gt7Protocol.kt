package io.github.jlai403.pocketsimengineer.core.protocol

object Gt7Protocol {
    val KEY = "Simulator Interface Packet GT7 v".encodeToByteArray()
    const val MAGIC = 0x47375330
    const val PACKET_A_SIZE = 296
    const val PORT_RECEIVE = 33740
    const val PORT_SEND = 33739
}
