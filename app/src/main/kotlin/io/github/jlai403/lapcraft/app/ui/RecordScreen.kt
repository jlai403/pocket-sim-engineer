package io.github.jlai403.lapcraft.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.BuildConfig
import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.capture.CaptureState
import io.github.jlai403.lapcraft.core.network.Ipv4
import io.github.jlai403.lapcraft.core.telemetry.TelemetryHealth

@Composable
fun RecordScreen(
    state: CaptureUiState,
    onSaveIp: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onOpenLap: (String, Int) -> Unit,
) {
    var ip by remember(state.ps5Ip) { mutableStateOf(state.ps5Ip) }
    var savedIp by remember { mutableStateOf<String?>(null) }
    var ipError by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    LaunchedEffect(state.telemetryRunning, state.telemetryError) {
        if (state.telemetryRunning || state.telemetryError != null) starting = false
    }

    fun saveIp(): String? {
        val normalized = Ipv4.normalize(ip)
        return if (normalized == null) {
            ipError = true
            savedIp = null
            null
        } else {
            ip = normalized
            ipError = false
            savedIp = normalized
            onSaveIp(normalized)
            normalized
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Record",
            subtitle = "Telemetry only · replay comes later",
        )

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = state.packetsPerSecond > 0)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.status, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "GT7 UDP 33740 · heartbeats every 100 ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(
                    label = "packets/s",
                    value = "%.0f".format(state.packetsPerSecond),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "lap",
                    value = state.lapCount.toString(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
                StatChip(
                    label = "speed m/s",
                    value = "%.0f".format(state.speedMps),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        StreamCard(state)
        if (state.telemetryError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            ) {
                Text(
                    state.telemetryError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        state.notice?.let { notice ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp),
            ) {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        SectionCard(title = "Console") {
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    savedIp = null
                    ipError = false
                },
                label = { Text("PS5 IPv4 address") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                enabled = !state.telemetryRunning,
                modifier = Modifier.fillMaxWidth().testTag("ps5-ip"),
                shape = RoundedCornerShape(14.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostPill(
                    "Save IP",
                    enabled = !state.telemetryRunning,
                    onClick = { saveIp() },
                )
                GhostPill(
                    text = when {
                        state.telemetryRunning -> "Disconnect"
                        starting -> "Starting…"
                        else -> "Connect"
                    },
                    onClick = {
                        if (state.telemetryRunning) {
                            onDisconnect()
                        } else {
                            saveIp()?.let { normalized ->
                                starting = true
                                onConnect(normalized)
                            }
                        }
                    },
                )
            }
            Text(
                when {
                    ipError -> "Enter a valid IPv4 address"
                    starting -> "Starting the listener…"
                    savedIp != null -> "Saved $savedIp"
                    state.telemetryRunning && state.connected -> "Connected to ${state.ps5Ip}"
                    state.telemetryRunning -> "Listening on ${state.ps5Ip}"
                    else -> "Saved when you connect"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ipError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        CaptureCard(
            state = state,
            onCapture = onCapture,
            onCancelCapture = onCancelCapture,
            onOpenLap = onOpenLap,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CaptureCard(
    state: CaptureUiState,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onOpenLap: (String, Int) -> Unit,
) {
    val canCapture = state.connected && state.lapStartObserved
    val hint = when {
        !state.telemetryRunning -> "Connect before capturing a lap."
        !state.connected -> "Waiting for the first packet."
        !state.lapStartObserved -> "Cross the start line to begin a lap."
        else -> "Captures this lap and saves it at the next line."
    }

    SectionCard(title = "Capture") {
        when (val capture = state.capture) {
            is CaptureState.Saved -> {
                Text(
                    "Saved ${lapTimeLabel(capture.lapTimeMs)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryPill(
                        text = "Capture another lap",
                        onClick = onCapture,
                        modifier = Modifier.weight(1f),
                        enabled = canCapture,
                    )
                    GhostPill(
                        text = "View lap",
                        onClick = { onOpenLap(capture.sessionId, 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            CaptureState.Capturing -> {
                Text(
                    "Capturing lap — finishes at the next line",
                    style = MaterialTheme.typography.titleMedium,
                )
                GhostPill("Cancel", onCancelCapture)
            }

            is CaptureState.Rejected -> {
                Text(
                    capture.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                PrimaryPill(
                    text = "Capture this lap",
                    onClick = onCapture,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCapture,
                )
            }

            CaptureState.Idle -> {
                PrimaryPill(
                    text = "Capture this lap",
                    onClick = onCapture,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCapture,
                )
            }
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreamCard(state: CaptureUiState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val health = TelemetryHealth.evaluate(
        connected = state.connected,
        packetsPerSecond = state.packetsPerSecond,
        lastPacketAgeMs = state.lastPacketAgeMs,
    )
    val statusColor = when {
        health.healthy -> MaterialTheme.colorScheme.primary
        !state.connected -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    SectionCard(
        title = "GT7 stream",
        action = {
            GhostPill(
                text = if (expanded) "Hide details" else "Show details",
                onClick = { expanded = !expanded },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(health.healthy)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    health.reason,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                )
                Text(
                    streamSummary(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.connected) {
                    Text(
                        if (state.telemetryRunning) {
                            "GT7 sends telemetry only while a race or time trial is on screen."
                        } else {
                            "Tap Connect to listen for GT7 telemetry."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!expanded) return@SectionCard

        MetricRow("Packet ID", state.packetId.toString())
        MetricRow("Car code", state.carCode.toString())
        MetricRow(
            "Flags",
            "0x%04X · %s".format(
                state.flags,
                if (state.flags and 1 != 0) "on track" else "off track",
            ),
        )
        MetricRow(
            "Inputs",
            "G%d · T%d%% · B%d%% · %.0f rpm".format(
                state.gear,
                state.throttle * 100 / 255,
                state.brake * 100 / 255,
                state.rpm,
            ),
        )
        MetricRow(
            "Packets",
            "%d accepted · %d decode errors".format(state.acceptedPackets, state.decodeErrors),
        )
        MetricRow(
            "Datagrams",
            "%d received · %d from other hosts".format(state.rawPackets, state.foreignPackets),
        )
        if (state.foreignPackets > 0 && state.lastForeignSource.isNotBlank()) {
            Text(
                "Packets are arriving from ${state.lastForeignSource}, not ${state.ps5Ip}. " +
                    "Check the console's current address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.lastDecodeFailure.isNotBlank()) {
            MetricRow("Last decode failure", state.lastDecodeFailure)
        }
        MetricRow(
            "Laps",
            "%d detected · GT7 lap %d · last %s".format(
                state.completedLaps,
                state.lapCount,
                lapTimeLabel(state.lastLapTimeMs),
            ),
        )

        SectionLabel("Recent events")
        if (state.events.isEmpty()) {
            Text(
                "No events yet. Connections, laps, and car state changes appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.events.take(6).forEach { event ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericText(
                        formatEventTime(event.atEpochMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(event.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GhostPill("Copy diagnostics", onClick = { copyDiagnostics(context, state) })
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumericText(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun streamSummary(state: CaptureUiState): String {
    val age = if (state.lastPacketAgeMs < 0) {
        "no packets yet"
    } else {
        "%d ms ago".format(state.lastPacketAgeMs)
    }
    return "%.1f pps · %d errors · %s".format(
        state.packetsPerSecond,
        state.decodeErrors,
        age,
    )
}

private fun copyDiagnostics(context: Context, state: CaptureUiState) {
    val text = buildString {
        appendLine(
            "LapCraft ${BuildConfig.VERSION_NAME} " +
                "(build ${BuildConfig.BUILD_NUMBER}, ${BuildConfig.GIT_SHA})",
        )
        appendLine("Connected: ${state.connected}")
        appendLine("Packets/s: %.1f".format(state.packetsPerSecond))
        appendLine("Accepted: ${state.acceptedPackets}, decode errors: ${state.decodeErrors}")
        appendLine("Last decode failure: ${state.lastDecodeFailure.ifBlank { "none" }}")
        appendLine(
            "Datagrams: ${state.rawPackets} received, ${state.foreignPackets} from other hosts " +
                "(${state.lastForeignSource.ifBlank { "none" }})",
        )
        appendLine("Last packet age: ${state.lastPacketAgeMs} ms")
        appendLine(
            "Packet ID: ${state.packetId}, car: ${state.carCode}, " +
                "flags: 0x%04X".format(state.flags),
        )
        appendLine(
            "Laps: ${state.completedLaps} detected, GT7 lap ${state.lapCount}, " +
                "last ${lapTimeLabel(state.lastLapTimeMs)}",
        )
        appendLine(
            "Speed: %.1f m/s, gear ${state.gear}, throttle ${state.throttle}, " +
                "brake ${state.brake}, rpm %.0f".format(state.speedMps, state.rpm),
        )
        appendLine("Capture: ${state.capture}")
        appendLine("Events:")
        state.events.take(5).forEach {
            appendLine("- ${formatEventTime(it.atEpochMs)} ${it.message}")
        }
        appendLine("Recent log:")
        Diagnostics.tail(context, 40).forEach { appendLine(it) }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("GT7 diagnostics", text))
    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
}
