package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** One replay source button that opens Files or the PlayStation App. */
@Composable
internal fun ReplaySourcePill(
    text: String,
    primary: Boolean,
    onFiles: () -> Unit,
    onPlayStation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        if (primary) {
            PrimaryPill(
                text = text,
                onClick = { open = true },
                modifier = modifier,
                trailingIcon = Icons.Filled.KeyboardArrowDown,
            )
        } else {
            GhostPill(
                text = text,
                onClick = { open = true },
                modifier = modifier,
                trailingIcon = Icons.Filled.KeyboardArrowDown,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Files", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Pick a video from this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    open = false
                    onFiles()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("PlayStation App", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Download the replay, then pick it here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    open = false
                    onPlayStation()
                },
            )
        }
    }
}
