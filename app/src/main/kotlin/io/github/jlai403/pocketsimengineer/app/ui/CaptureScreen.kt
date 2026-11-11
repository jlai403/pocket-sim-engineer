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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onIpChanged: (String) -> Unit,
    onArm: () -> Unit,
    onTestRecord: () -> Unit,
    onStop: () -> Unit,
) {
    var ip by remember(state.ps5Ip) { mutableStateOf(state.ps5Ip) }
    var showInstructions by rememberSaveable { mutableStateOf(false) }

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

        Warnings(state)

        SectionCard(title = "Console") {
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    onIpChanged(it)
                },
                label = { Text("PS5 IPv4 address") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        }

        PrimaryPill(
            text = "Arm recording",
            onClick = onArm,
            enabled = ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostPill("Test 10 s", onTestRecord, Modifier.weight(1f))
            GhostPill("Stop", onStop, Modifier.weight(1f))
        }

        RecordingInstructions(showInstructions) { showInstructions = it }
        Spacer(Modifier.height(8.dp))
    }
}

private val recordingSteps = listOf(
    "Connect Chiaki-Up to the PS5 and open GT7.",
    "Enter the PS5 IPv4 address above.",
    "Tap Arm recording, then allow screen capture.",
    "Switch to Chiaki-Up and start driving. Recording begins when GT7 reports the car on track.",
    "Drive your laps. The notification shows the current lap.",
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
                "7 steps · about a minute before your first lap",
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
