package io.github.jlai403.pocketsimengineer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.updater.UpdateManager

@Composable
fun App(
    capture: CaptureUiState,
    onSaveIp: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    sharedVideoUri: String?,
    onSharedVideoHandled: () -> Unit,
    update: UpdateManager.State,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    val activeTab = when (screen) {
        Screen.Library -> 0
        Screen.Live -> 1
        Screen.Settings -> 2
        else -> null
    }

    LaunchedEffect(sharedVideoUri) {
        val uri = sharedVideoUri ?: return@LaunchedEffect
        screen = Screen.ImportPick(uri)
        onSharedVideoHandled()
    }

    BackHandler(enabled = screen != Screen.Library && screen !is Screen.ImportSync) {
        screen = when (val current = screen) {
            is Screen.ImportSync -> Screen.Player(current.sessionId, current.lapIndex)
            else -> Screen.Library
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (val current = screen) {
                        Screen.Library -> LibraryScreen(
                            onOpenLap = { id, index -> screen = Screen.Player(id, index) },
                        )

                        is Screen.ImportPick -> ImportPickScreen(
                            uri = current.uri,
                            onPick = { id, index ->
                                screen = Screen.ImportSync(id, index, current.uri)
                            },
                            onClose = { screen = Screen.Library },
                        )

                        is Screen.ImportSync -> ImportSyncScreen(
                            sessionId = current.sessionId,
                            lapIndex = current.lapIndex,
                            sourceUri = current.sourceUri,
                            onDone = { screen = Screen.Player(current.sessionId, current.lapIndex) },
                            onCancel = { screen = Screen.Library },
                        )

                        is Screen.Player -> LapPlayerScreen(
                            sessionId = current.sessionId,
                            lapIndex = current.lapIndex,
                            onBack = { screen = Screen.Library },
                            onImportSync = {
                                screen = Screen.ImportSync(current.sessionId, current.lapIndex, it)
                            },
                        )

                        Screen.Live -> LiveScreen(
                            state = capture,
                            onSaveIp = onSaveIp,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                            onCapture = onCapture,
                            onCancelCapture = onCancelCapture,
                            onOpenLap = { id, index -> screen = Screen.Player(id, index) },
                        )

                        Screen.Settings -> SettingsScreen(
                            update = update,
                            onCheckUpdate = onCheckUpdate,
                            onDownloadUpdate = onDownloadUpdate,
                            onInstallUpdate = onInstallUpdate,
                        )
                    }
                }
                UpdatePrompt(
                    update = update,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                )
                if (activeTab != null) {
                    AppBottomBar(selected = activeTab) { tab ->
                        screen = when (tab) {
                            0 -> Screen.Library
                            1 -> Screen.Live
                            else -> Screen.Settings
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePrompt(
    update: UpdateManager.State,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val available = update as? UpdateManager.State.Available ?: return
    if (dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Update available") },
        text = {
            Text(
                buildString {
                    append(
                        "Version ${available.manifest.versionName} " +
                            "(build ${available.manifest.versionCode}) is available.",
                    )
                    if (available.manifest.notes.isNotBlank()) append(" ${available.manifest.notes}")
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismissed = true
                    onDownload()
                },
            ) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text("Later") }
        },
    )
}

@Composable
private fun AppBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Library") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            label = { Text("Live") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") },
            colors = colors,
        )
    }
}
