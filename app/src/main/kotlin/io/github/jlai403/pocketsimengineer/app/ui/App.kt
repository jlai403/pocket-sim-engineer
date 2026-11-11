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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App(
    capture: CaptureUiState,
    onSaveIp: (String) -> Unit,
    onArm: (String) -> Unit,
    onTestRecord: () -> Unit,
    onStop: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    val activeTab = when (screen) {
        Screen.Library -> 0
        Screen.Capture -> 1
        Screen.Settings -> 2
        else -> null
    }

    BackHandler(enabled = screen != Screen.Library) {
        screen = when (val current = screen) {
            is Screen.Player -> Screen.Session(current.sessionId)
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
                            onOpenSession = { screen = Screen.Session(it) },
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
                            onSaveIp = onSaveIp,
                            onArm = onArm,
                            onTestRecord = onTestRecord,
                            onStop = onStop,
                        )

                        Screen.Settings -> SettingsScreen()
                    }
                }
                if (activeTab != null) {
                    AppBottomBar(selected = activeTab) { tab ->
                        screen = when (tab) {
                            0 -> Screen.Library
                            1 -> Screen.Capture
                            else -> Screen.Settings
                        }
                    }
                }
            }
        }
    }
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
            label = { Text("Record") },
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
