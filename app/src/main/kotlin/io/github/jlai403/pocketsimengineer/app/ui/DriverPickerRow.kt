package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileLinks
import io.github.jlai403.pocketsimengineer.app.profile.countryFlagEmoji

/**
 * The driver identity row. Pass [onOpenLink] to make the name open the public
 * profile links for the assigned PSN ID.
 */
@Composable
internal fun DriverPickerRow(
    driverPsnId: String?,
    profile: DriverProfile?,
    owner: DriverProfile?,
    profiles: List<DriverProfile>,
    onChange: (DriverProfile?) -> Unit,
    onAddDriver: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val linked = onOpenLink != null && driverPsnId != null

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            countryFlagEmoji(profile?.countryCode)?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (linked) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Open GT profile") { menuOpen = true }
                                .testTag("driver-profile")
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        profile?.nickname ?: driverPsnId ?: "No driver",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (linked) {
                        Text(
                            "↗",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    profile?.psnId ?: driverPsnId ?: "Defaults to your profile when set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                GhostPill(
                    text = if (driverPsnId == null) "Set driver" else "Change",
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("driver-menu"),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    driverOptions(owner, profiles).forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        if (option.psnId.equals(owner?.psnId, ignoreCase = true)) {
                                            "${option.nickname} (you)"
                                        } else {
                                            option.nickname
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        option.psnId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = {
                                Text(countryFlagEmoji(option.countryCode).orEmpty())
                            },
                            trailingIcon = {
                                if (option.psnId.equals(driverPsnId, ignoreCase = true)) Text("✓")
                            },
                            onClick = {
                                menuOpen = false
                                onChange(option)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Add driver…") },
                        onClick = {
                            menuOpen = false
                            onAddDriver()
                        },
                    )
                    if (driverPsnId != null) {
                        DropdownMenuItem(
                            text = { Text("Clear driver") },
                            onClick = {
                                menuOpen = false
                                onChange(null)
                            },
                        )
                    }
                    if (linked && onOpenLink != null && driverPsnId != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("GT GridStats") },
                            onClick = {
                                menuOpen = false
                                onOpenLink(DriverProfileLinks.gridStats(driverPsnId))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("GTSH-Rank") },
                            onClick = {
                                menuOpen = false
                                onOpenLink(DriverProfileLinks.gtshRank(driverPsnId))
                            },
                        )
                    }
                }
            }
        }
        if (driverPsnId == null && owner != null) {
            GhostPill(
                text = "Use my profile",
                onClick = { onChange(owner) },
                modifier = Modifier.testTag("driver-use-owner"),
            )
        }
    }
}

private fun driverOptions(
    owner: DriverProfile?,
    profiles: List<DriverProfile>,
): List<DriverProfile> = buildList {
    owner?.let(::add)
    profiles.forEach { profile ->
        if (none { it.psnId.equals(profile.psnId, ignoreCase = true) }) add(profile)
    }
}
