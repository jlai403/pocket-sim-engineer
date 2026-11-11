package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.BuildConfig
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.settings.VideoCodec
import io.github.jlai403.pocketsimengineer.app.settings.VideoQuality
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Capture, video, and units",
        )

        SectionCard(title = "Console") {
            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    settings.ps5Ip = it
                },
                label = { Text("PS5 IPv4 address") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        }

        SectionCard(title = "Video") {
            SectionLabel("Resolution")
            SegmentedChoice(
                options = listOf("1080p60", "720p60"),
                selectedIndex = if (quality == VideoQuality.P1080) 0 else 1,
                onSelect = {
                    quality = if (it == 0) VideoQuality.P1080 else VideoQuality.P720
                    settings.quality = quality
                },
            )
            SectionLabel("Codec")
            SegmentedChoice(
                options = listOf("H.264", "HEVC"),
                selectedIndex = if (codec == VideoCodec.H264) 0 else 1,
                onSelect = {
                    codec = if (it == 0) VideoCodec.H264 else VideoCodec.HEVC
                    settings.codec = codec
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bitrate", style = MaterialTheme.typography.titleMedium)
                Text(
                    "  ${bitrate.roundToInt()} Mbps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

        SectionCard(title = "Recording") {
            SettingToggle(
                label = "Keep raw recording",
                checked = keepRaw,
                modifier = Modifier.testTag("keep-raw"),
            ) {
                keepRaw = it
                settings.keepRaw = it
            }
        }

        SectionCard(title = "Display") {
            SettingToggle(
                label = "Imperial units",
                checked = imperial,
                modifier = Modifier.testTag("imperial"),
            ) {
                imperial = it
                settings.imperialUnits = it
            }
        }

        SectionCard(title = "Build") {
            BuildRow("Version", BuildConfig.VERSION_NAME)
            BuildRow("Build number", BuildConfig.BUILD_NUMBER)
            BuildRow("Commit", BuildConfig.GIT_SHA)
        }
    }
}

@Composable
private fun BuildRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumericText(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}
