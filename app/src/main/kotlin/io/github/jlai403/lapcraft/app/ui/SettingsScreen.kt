package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jlai403.lapcraft.BuildConfig
import io.github.jlai403.lapcraft.core.profile.DriverProfile
import io.github.jlai403.lapcraft.app.profile.GridStatsProfileClient
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.app.updater.UpdateManager
import io.github.jlai403.lapcraft.core.network.Ipv4

@Composable
fun SettingsScreen(
    update: UpdateManager.State = UpdateManager.State.Idle,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    lookupProfile: suspend (String) -> DriverProfile = { GridStatsProfileClient().fetch(it) },
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var ip by remember { mutableStateOf(settings.ps5Ip) }
    var savedIp by remember { mutableStateOf<String?>(null) }
    var ipError by remember { mutableStateOf(false) }
    fun saveIp() {
        val normalized = Ipv4.normalize(ip)
        if (normalized == null) {
            ipError = true
            savedIp = null
        } else {
            ip = normalized
            ipError = false
            savedIp = normalized
            settings.savePs5Ip(normalized)
        }
    }
    var imperial by remember { mutableStateOf(settings.imperialUnits) }
    var haptics by remember { mutableStateOf(settings.hapticsEnabled) }
    var defaultSpeed by remember { mutableStateOf(settings.defaultPlaybackSpeed) }
    var speedMenu by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf(settings.telemetryHud) }
    var chartWindowSeconds by remember { mutableStateOf(settings.chartWindowSeconds) }
    var chartWindowMenu by remember { mutableStateOf(false) }
    var previewEmpty by remember { mutableStateOf(settings.previewEmptyLibrary) }
    var developerTools by remember { mutableStateOf(settings.developerTools) }
    var replayPreset by remember { mutableStateOf(settings.replayPreset) }
    var replayKeyframes by remember { mutableStateOf(settings.replayKeyframes) }
    var replayAudio by remember { mutableStateOf(settings.replayAudio) }
    var replayResolution by remember { mutableStateOf(settings.replayResolution) }
    var replayCodec by remember { mutableStateOf(settings.replayCodec) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Console, profile, units, and updates",
        )

        DriverProfileSection(lookupProfile = lookupProfile)

        LibrarySection()

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
                modifier = Modifier.fillMaxWidth().testTag("settings-ps5-ip"),
                shape = RoundedCornerShape(14.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostPill("Save IP", onClick = { saveIp() })
                Text(
                    when {
                        ipError -> "Enter a valid IPv4 address"
                        savedIp != null -> "Saved $savedIp"
                        else -> "Used for telemetry heartbeats"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ipError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
            SettingToggle(
                label = "Frame ruler haptics",
                checked = haptics,
                modifier = Modifier.testTag("haptics"),
            ) {
                haptics = it
                settings.hapticsEnabled = it
            }
            Text(
                "Ticks as you drag the ruler.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Default playback speed",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Box {
                    GhostPill(
                        text = playbackSpeedLabel(defaultSpeed),
                        onClick = { speedMenu = true },
                        modifier = Modifier.testTag("default-playback-speed"),
                        trailingIcon = Icons.Filled.KeyboardArrowDown,
                    )
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        PLAYBACK_SPEEDS.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(playbackSpeedLabel(speed)) },
                                onClick = {
                                    defaultSpeed = speed
                                    settings.defaultPlaybackSpeed = speed
                                    speedMenu = false
                                },
                            )
                        }
                    }
                }
            }
            SettingToggle(
                label = "Telemetry HUD",
                checked = hud,
                modifier = Modifier.testTag("telemetry-hud"),
            ) {
                hud = it
                settings.telemetryHud = it
            }
            Text(
                "Speed, gear, and pedal sparklines over the replay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Chart window",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Box {
                    GhostPill(
                        text = "\u00b1${chartWindowSeconds}s",
                        onClick = { chartWindowMenu = true },
                        modifier = Modifier.testTag("chart-window"),
                        trailingIcon = Icons.Filled.KeyboardArrowDown,
                    )
                    DropdownMenu(expanded = chartWindowMenu, onDismissRequest = { chartWindowMenu = false }) {
                        listOf(2, 3, 5, 10).forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text("\u00b1${seconds}s") },
                                onClick = {
                                    chartWindowSeconds = seconds
                                    settings.chartWindowSeconds = seconds
                                    chartWindowMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Text(
                "Time shown either side of the cursor on the telemetry charts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Replay compression") {
            Text(
                "Defaults for new imports. The import screen can override them for one lap. Existing laps are left untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReplayCompressionControls(
                preset = replayPreset,
                keyframes = replayKeyframes,
                audio = replayAudio,
                resolution = replayResolution,
                codec = replayCodec,
                highlightChanges = true,
                onPresetChange = {
                    replayPreset = it
                    settings.replayPreset = it
                },
                onResolutionChange = {
                    replayResolution = it
                    settings.replayResolution = it
                },
                onCodecChange = {
                    replayCodec = it
                    settings.replayCodec = it
                },
                onKeyframesChange = {
                    replayKeyframes = it
                    settings.replayKeyframes = it
                },
                onAudioChange = {
                    replayAudio = it
                    settings.replayAudio = it
                },
            )
        }

        if (BuildConfig.UPDATE_CHANNEL != "stable") {
            SectionCard(title = "Developer") {
                SettingToggle(
                    label = "Developer tools",
                    checked = developerTools,
                    modifier = Modifier.testTag("dev-developer-tools"),
                ) {
                    developerTools = it
                    settings.developerTools = it
                }
                Text(
                    "Adds maintenance actions such as re-encoding a lap clip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingToggle(
                    label = "Preview empty library",
                    checked = previewEmpty,
                    modifier = Modifier.testTag("dev-preview-empty"),
                ) {
                    previewEmpty = it
                    settings.previewEmptyLibrary = it
                }
                Text(
                    "Shows the empty state while your laps stay saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Updates") {
            Text(
                updateSummary(update),
                style = MaterialTheme.typography.bodySmall,
                color = if (update is UpdateManager.State.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            when (update) {
                is UpdateManager.State.Available -> GhostPill(
                    text = "Download ${update.manifest.versionName}",
                    onClick = onDownloadUpdate,
                )
                is UpdateManager.State.Ready -> GhostPill(
                    text = "Install update",
                    onClick = onInstallUpdate,
                )
                is UpdateManager.State.Downloading -> GhostPill(
                    text = "Downloading ${update.percent}%",
                    enabled = false,
                    onClick = {},
                )
                is UpdateManager.State.Checking -> GhostPill(
                    text = "Checking…",
                    enabled = false,
                    onClick = {},
                )
                is UpdateManager.State.Installing -> GhostPill(
                    text = "Installing…",
                    enabled = false,
                    onClick = {},
                )
                else -> GhostPill("Check for updates", onClick = onCheckUpdate)
            }
        }

        SectionCard(title = "Build") {
            BuildRow("Version", BuildConfig.VERSION_NAME)
            BuildRow("Version code", BuildConfig.VERSION_CODE.toString())
            BuildRow("Update channel", BuildConfig.UPDATE_CHANNEL)
            BuildRow("Build number", BuildConfig.BUILD_NUMBER)
            BuildRow("Commit", BuildConfig.GIT_SHA)
        }
    }
}

private fun updateSummary(update: UpdateManager.State): String = when (update) {
    is UpdateManager.State.Idle -> "Checks the rolling debug build once a day."
    is UpdateManager.State.Checking -> "Checking for updates…"
    is UpdateManager.State.UpToDate -> "You are on the latest build."
    is UpdateManager.State.Available -> buildString {
        append("Version ${update.manifest.versionName} (build ${update.manifest.versionCode}) is available.")
        if (update.manifest.notes.isNotBlank()) append(" ${update.manifest.notes}")
    }
    is UpdateManager.State.Downloading -> "Downloading ${update.percent}%…"
    is UpdateManager.State.Ready -> "Downloaded. Install hands it to Android's installer."
    is UpdateManager.State.Installing -> "Waiting for the system installer…"
    is UpdateManager.State.Failed -> update.message
}

@Composable
private fun BuildRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumericText(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
