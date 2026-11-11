package io.github.jlai403.pocketsimengineer.app.ui

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
import androidx.compose.foundation.shape.CircleShape
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.BuildConfig
import io.github.jlai403.pocketsimengineer.core.network.Ipv4
import io.github.jlai403.pocketsimengineer.core.telemetry.TelemetryHealth

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onSaveIp: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartRecording: () -> Unit,
    onTestRecord: () -> Unit,
    onStop: () -> Unit,
) {
    var ip by remember(state.ps5Ip) { mutableStateOf(state.ps5Ip) }
    var savedIp by remember { mutableStateOf<String?>(null) }
    var ipError by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var showInstructions by rememberSaveable { mutableStateOf(false) }

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
            subtitle = "Telemetry and screen capture",
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

        Warnings(state)

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
                    enabled = !state.recording,
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

        PrimaryPill(
            text = if (state.recording) "Recording…" else "Start recording",
            onClick = onStartRecording,
            enabled = state.connected && !state.recording,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                state.recording -> "Tap Stop to finish the session."
                !state.telemetryRunning -> "Connect before recording."
                !state.connected -> "Waiting for the first packet."
                else -> "Recording begins when the car is on track."
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostPill("Test 10 s", onTestRecord, Modifier.weight(1f))
            GhostPill("Stop", onStop, Modifier.weight(1f), enabled = state.recording)
        }

        RecordingInstructions(showInstructions) { showInstructions = it }
        Spacer(Modifier.height(8.dp))
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
            "Pocket Sim Engineer ${BuildConfig.VERSION_NAME} " +
                "(build ${BuildConfig.BUILD_NUMBER}, ${BuildConfig.GIT_SHA})",
        )
        appendLine("Connected: ${state.connected}")
        appendLine("Packets/s: %.1f".format(state.packetsPerSecond))
        appendLine("Accepted: ${state.acceptedPackets}, decode errors: ${state.decodeErrors}")
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
        appendLine("Events:")
        state.events.take(5).forEach {
            appendLine("- ${formatEventTime(it.atEpochMs)} ${it.message}")
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("GT7 diagnostics", text))
    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
}

private val recordingSteps = listOf(
    "Connect Chiaki-Up to the PS5 and open GT7.",
    "In Chiaki-Up, swipe down from the top edge and turn On-Screen Controls off. The bar fades after a moment.",
    "Enter a race or time trial and park on track. GT7 sends telemetry only while a car is on screen.",
    "Enter the PS5 IPv4 address above and tap Connect.",
    "Wait for the stream card or the notification to show about 60 pps.",
    "Tap Start recording, then allow screen capture.",
    "Switch back to Chiaki-Up and drive. Recording begins when the car is on track.",
    "Return to the menu for 5 seconds, or tap Stop in the notification.",
    "Wait for \"Finalizing…\" to finish, then open Library and pick a lap.",
)

@Composable
private fun RecordingInstructions(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    SectionCard(
        title = "How to record",
        action = {
            GhostPill(
                text = if (expanded) "Hide steps" else "Show steps",
                onClick = { onExpandedChange(!expanded) },
            )
        },
    ) {
        if (!expanded) {
            Text(
                "9 steps · about a minute before your first lap",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        recordingSteps.forEachIndexed { index, step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    NumericText(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    step,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Keep in mind",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Keep Chiaki-Up in front. Locking the screen or stopping projection ends capture.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "If Android blocks internal audio, the lap records silently and a warning appears.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Chiaki-Up's bar is recorded if it stays on screen. Swipe it away before a lap; swipe from the top to get the controls back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Test 10 s only verifies capture; it does not create a library session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Warnings(state: CaptureUiState) {
    val warning = state.audioWarning ?: state.notice
    if (warning != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(14.dp),
        ) {
            Text(
                warning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
