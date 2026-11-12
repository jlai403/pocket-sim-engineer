package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.countryFlagEmoji
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import kotlin.math.roundToInt

/** One lap. Tap opens it, right swipe edits, left swipe asks to delete. Long press and the accessibility actions expose the same two edits. */
@Composable
internal fun LapCard(
    lap: LapSummary,
    driver: DriverProfile?,
    fastest: Boolean,
    deltaMs: Int?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    var width by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Snap under the finger, spring back to zero on release. A plain state target
    // cannot race a late drag event the way a queued snapTo could.
    val offset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (dragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "lap-card-swipe",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .testTag("lap-card"),
    ) {
        if (offset != 0f) {
            val edit = offset > 0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (edit) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    )
                    .testTag("lap-card-action")
                    .padding(horizontal = 20.dp),
                contentAlignment = if (edit) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (edit) Icons.Filled.Edit else Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (edit) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        if (edit) "Edit" else "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (edit) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        val limit = width * 0.6f
                        dragOffset = (dragOffset + delta).coerceIn(-limit, limit)
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = {
                        val threshold = width * 0.35f
                        val edit = dragOffset > threshold
                        val delete = dragOffset < -threshold
                        dragging = false
                        dragOffset = 0f
                        when {
                            edit -> onEdit()
                            delete -> onDelete()
                        }
                    },
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit lap") {
                            onEdit()
                            true
                        },
                        CustomAccessibilityAction("Delete lap") {
                            onDelete()
                            true
                        },
                    )
                }
                .combinedClickable(
                    onClickLabel = "Open lap",
                    onLongClickLabel = "Lap actions",
                    onLongClick = { menuOpen = true },
                    onClick = onOpen,
                )
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    NumericText(
                        lapTimeLabel(lap.lapTimeMs),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.weight(1f),
                    )
                    DriverLabel(lap.driverPsnId, driver)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lap.trackLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        fastest -> FastestBadge()
                        deltaMs != null -> NumericText(
                            "+%.3f".format(deltaMs / 1000.0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${lap.carName} · ${formatDate(lap.createdAt).substringBefore(' ')}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        lap.hasReplay -> ReplayState("Replay", MaterialTheme.colorScheme.primary)
                        lap.replayPending -> ReplayState("Sync pending", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit lap") },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete lap") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun DriverLabel(psnId: String?, driver: DriverProfile?) {
    val name = driver?.nickname ?: psnId
    Row(
        modifier = Modifier.widthIn(max = 132.dp).testTag("lap-driver"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        countryFlagEmoji(driver?.countryCode)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            name ?: "Driver not set",
            style = MaterialTheme.typography.bodyMedium,
            color = if (name == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FastestBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            "Fastest lap",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ReplayState(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
