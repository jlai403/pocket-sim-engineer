package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.app.service.TelemetryUiState

object StreamNotificationText {
    fun summary(base: String, state: TelemetryUiState): String =
        if (state.connected) {
            "%s · %.0f pps · %d err".format(base, state.packetsPerSecond, state.decodeErrors)
        } else {
            "$base · waiting for GT7"
        }

    fun details(state: TelemetryUiState, lastEvent: String?): String = buildString {
        appendLine(if (state.connected) "Streaming" else "Waiting for GT7 telemetry")
        appendLine(
            "Packets/s: %.1f · errors: %d".format(
                state.packetsPerSecond,
                state.decodeErrors,
            ),
        )
        appendLine(
            if (state.lastPacketAgeMs < 0) {
                "Last packet: none yet"
            } else {
                "Last packet: ${state.lastPacketAgeMs} ms ago"
            },
        )
        appendLine(
            "Packet ID ${state.packetId} · car ${state.carCode} · " +
                "flags 0x%04X (%s)".format(
                    state.flags,
                    if (state.flags and 1 != 0) "on track" else "off track",
                ),
        )
        appendLine("Lap ${state.lapCount} · last ${lapTime(state.lastLapTimeMs)}")
        if (lastEvent != null) append("Last event: $lastEvent")
    }.trim()

    fun lapTime(ms: Int): String =
        if (ms <= 0) "-" else "%d:%02d.%03d".format(ms / 60_000, (ms % 60_000) / 1_000, ms % 1_000)
}
