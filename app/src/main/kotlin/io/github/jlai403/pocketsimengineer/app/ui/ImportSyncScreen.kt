package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Placeholder replaced by the frame-step sync screen in Task 11. */
@Composable
fun ImportSyncScreen(
    sessionId: String,
    lapIndex: Int,
    sourceUri: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Replay received",
            subtitle = "Lap $lapIndex · sync is the next step",
            onBack = onCancel,
        )
        SectionCard {
            Text(
                "The replay is ready. The frame-step sync screen lands in the next task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostPill("Close", onClick = onDone)
        }
    }
}
