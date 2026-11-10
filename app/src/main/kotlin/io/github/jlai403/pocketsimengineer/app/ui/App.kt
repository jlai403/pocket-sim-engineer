package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SpikeUiState(
    val ps5Ip: String = "",
    val status: String = "Waiting for telemetry",
    val packetsPerSecond: Double = 0.0,
    val lapCount: Int = 0,
    val speedMps: Float = 0f,
    val recording: Boolean = false,
    val audioWarning: String? = null,
)

@Composable
fun PocketSimEngineerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
fun App(
    state: SpikeUiState = SpikeUiState(),
    onIpChanged: (String) -> Unit = {},
    onArm: () -> Unit = {},
    onTestRecord: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    var ip by remember(state.ps5Ip) { mutableStateOf(state.ps5Ip) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pocket Sim Engineer", style = MaterialTheme.typography.headlineMedium)
            Text(state.status, style = MaterialTheme.typography.bodyLarge)
            if (state.audioWarning != null) {
                Text(state.audioWarning, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    onIpChanged(it)
                },
                label = { Text("PS5 IPv4 address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Packets/s: %.1f   Lap: %d   Speed: %.1f m/s".format(
                    state.packetsPerSecond,
                    state.lapCount,
                    state.speedMps,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onArm) { Text("Arm") }
                Button(onClick = onTestRecord) { Text("Test record 10 s") }
                Button(onClick = onStop) { Text("Stop") }
            }
        }
    }
}
