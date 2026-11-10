package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.settings.VideoCodec
import io.github.jlai403.pocketsimengineer.app.settings.VideoQuality
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var ip by remember { mutableStateOf(settings.ps5Ip) }
    var quality by remember { mutableStateOf(settings.quality) }
    var codec by remember { mutableStateOf(settings.codec) }
    var bitrate by remember { mutableFloatStateOf(settings.bitrateMbps.toFloat()) }
    var keepRaw by remember { mutableStateOf(settings.keepRaw) }
    var imperial by remember { mutableStateOf(settings.imperialUnits) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        OutlinedTextField(
            value = ip,
            onValueChange = {
                ip = it
                settings.ps5Ip = it
            },
            label = { Text("PS5 IPv4 address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingRow("Resolution") {
            TextButton(onClick = {
                quality = VideoQuality.P1080
                settings.quality = quality
            }) {
                Text(if (quality == VideoQuality.P1080) "[1080p60]" else "1080p60")
            }
            TextButton(onClick = {
                quality = VideoQuality.P720
                settings.quality = quality
            }) {
                Text(if (quality == VideoQuality.P720) "[720p60]" else "720p60")
            }
        }

        SettingRow("Codec") {
            TextButton(onClick = {
                codec = VideoCodec.H264
                settings.codec = codec
            }) {
                Text(if (codec == VideoCodec.H264) "[H.264]" else "H.264")
            }
            TextButton(onClick = {
                codec = VideoCodec.HEVC
                settings.codec = codec
            }) {
                Text(if (codec == VideoCodec.HEVC) "[HEVC]" else "HEVC")
            }
        }

        Column {
            Text("Bitrate: ${bitrate.roundToInt()} Mbps")
            Slider(
                value = bitrate,
                onValueChange = {
                    bitrate = it
                    settings.bitrateMbps = it.roundToInt()
                },
                valueRange = 4f..20f,
                steps = 15,
            )
        }

        SettingToggle("Keep raw recording", keepRaw) {
            keepRaw = it
            settings.keepRaw = it
        }

        SettingToggle("Imperial units", imperial) {
            imperial = it
            settings.imperialUnits = it
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
