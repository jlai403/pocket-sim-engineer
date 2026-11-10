package io.github.jlai403.pocketsimengineer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PocketSimEngineerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
fun App(
    capture: CaptureUiState,
    onIpChanged: (String) -> Unit,
    onArm: () -> Unit,
    onTestRecord: () -> Unit,
    onStop: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    BackHandler(enabled = screen != Screen.Library) {
        screen = when (val current = screen) {
            is Screen.Player -> Screen.Session(current.sessionId)
            else -> Screen.Library
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            Screen.Library -> LibraryScreen(
                onOpenSession = { screen = Screen.Session(it) },
                onCapture = { screen = Screen.Capture },
                onSettings = { screen = Screen.Settings },
            )

            is Screen.Session -> SessionScreen(
                sessionId = current.sessionId,
                onBack = { screen = Screen.Library },
                onOpenLap = { screen = Screen.Player(current.sessionId, it) },
                onDeleted = { screen = Screen.Library },
            )

            is Screen.Player -> LapPlayerScreen(
                sessionId = current.sessionId,
                lapIndex = current.lapIndex,
                onBack = { screen = Screen.Session(current.sessionId) },
            )

            Screen.Capture -> CaptureScreen(
                state = capture,
                onIpChanged = onIpChanged,
                onArm = onArm,
                onTestRecord = onTestRecord,
                onStop = onStop,
                onBack = { screen = Screen.Library },
                onSettings = { screen = Screen.Settings },
            )

            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Library })
        }
    }
}

@Composable
private fun CaptureScreen(
    state: CaptureUiState,
    onIpChanged: (String) -> Unit,
    onArm: () -> Unit,
    onTestRecord: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    var ip by remember(state.ps5Ip) { mutableStateOf(state.ps5Ip) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Record", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Settings") }
        }

        Text(state.status, style = MaterialTheme.typography.bodyLarge)
        state.audioWarning?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
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
            Button(onClick = onArm, enabled = ip.isNotBlank()) { Text("Arm") }
            Button(onClick = onTestRecord) { Text("Test 10 s") }
            Button(onClick = onStop) { Text("Stop") }
        }

        Text(
            "Arm grants screen capture once. Recording starts automatically when GT7 reports " +
                "the car on track.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
