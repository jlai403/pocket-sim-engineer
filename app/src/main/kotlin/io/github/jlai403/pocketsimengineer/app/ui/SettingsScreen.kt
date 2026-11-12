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
import androidx.compose.foundation.verticalScroll
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
import io.github.jlai403.pocketsimengineer.BuildConfig
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.updater.UpdateManager
import io.github.jlai403.pocketsimengineer.core.network.Ipv4

@Composable
fun SettingsScreen(
    update: UpdateManager.State = UpdateManager.State.Idle,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Console, units, and updates",
        )

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
