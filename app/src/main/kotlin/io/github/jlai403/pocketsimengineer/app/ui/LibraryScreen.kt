package io.github.jlai403.pocketsimengineer.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileLinks
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.app.profile.countryFlagEmoji
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.track.TrackCatalog
import io.github.jlai403.pocketsimengineer.app.track.TrackNamer
import io.github.jlai403.pocketsimengineer.core.session.LapFilter
import io.github.jlai403.pocketsimengineer.core.session.LapSort
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import io.github.jlai403.pocketsimengineer.core.session.applyFilter
import io.github.jlai403.pocketsimengineer.core.session.applySort
import io.github.jlai403.pocketsimengineer.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    onOpenLap: (String, Int) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    val settings = remember { SettingsStore(context) }
    val profileStore = remember { DriverProfileStore(context) }
    val namer = remember { TrackNamer(store, TrackCatalog(context)) }
    val owner = remember { profileStore.owner() }
    val profiles = remember {
        profileStore.profiles().associateBy { it.psnId.lowercase() }
    }
    var profileMenu by remember { mutableStateOf(false) }
    var laps by remember { mutableStateOf<List<LapSummary>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(LapFilter()) }
    var sort by remember { mutableStateOf(LapSort.Newest) }
    var trackMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LapSummary?>(null) }
    var deleting by remember { mutableStateOf<LapSummary?>(null) }
    var choosing by remember { mutableStateOf<LapSummary?>(null) }
    var choices by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        if (settings.trackIdentificationVersion < TrackNamer.VERSION) {
            runCatching { namer.identifyUnknownLaps() }
            settings.trackIdentificationVersion = TrackNamer.VERSION
        }
        laps = withContext(Dispatchers.IO) { store.summaries() }
    }

    fun rename(lap: LapSummary, name: String) {
        scope.launch {
            val renamed = withContext(Dispatchers.IO) {
                runCatching { store.rename(lap.sessionId, name) }.getOrNull()
            }
            if (renamed == null) message = "Track name could not be saved."
            reload++
        }
    }

    fun chooseTrack(lap: LapSummary) {
        if (lap.trackLabel != TrackNamer.UNKNOWN) {
            renaming = lap
            renameText = lap.trackLabel
            return
        }
        choosing = lap
        choices = null
        scope.launch {
            choices = withContext(Dispatchers.IO) {
                runCatching { namer.candidates(lap.sessionId, lap.lapIndex) }
                    .getOrDefault(emptyList())
            }
        }
    }

    fun openProfile(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) {
            message = "No app can open that link."
        }
    }

    val all = laps
    val visible = all?.applyFilter(filter)?.applySort(sort)
    val tracks = all?.map { it.trackLabel }?.distinct()?.sorted().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Laps",
            subtitle = "One lap per card · replay optional",
            actions = {
                GhostPill("Refresh", onClick = { reload++ })
            },
        )

        DriverProfileHeader(
            profile = owner,
            menuOpen = profileMenu,
            onAdd = onOpenSettings,
            onOpenMenu = { profileMenu = true },
            onDismissMenu = { profileMenu = false },
            onGridStats = {
                profileMenu = false
                owner?.let { openProfile(DriverProfileLinks.gridStats(it.psnId)) }
            },
            onGtshRank = {
                profileMenu = false
                owner?.let { openProfile(DriverProfileLinks.gtshRank(it.psnId)) }
            },
            onEdit = {
                profileMenu = false
                onOpenSettings()
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.hasReplay == null,
                onClick = { filter = filter.copy(hasReplay = null) },
                label = { Text("All") },
            )
            FilterChip(
                selected = filter.hasReplay == true,
                onClick = { filter = filter.copy(hasReplay = true) },
                label = { Text("With replay") },
            )
            FilterChip(
                selected = filter.hasReplay == false,
                onClick = { filter = filter.copy(hasReplay = false) },
                label = { Text("Without replay") },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                GhostPill(
                    text = filter.trackLabel ?: "All tracks",
                    onClick = { trackMenu = true },
                )
                DropdownMenu(expanded = trackMenu, onDismissRequest = { trackMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("All tracks") },
                        onClick = {
                            filter = filter.copy(trackLabel = null)
                            trackMenu = false
                        },
                    )
                    tracks.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(track) },
                            onClick = {
                                filter = filter.copy(trackLabel = track)
                                trackMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                GhostPill(text = sort.label, onClick = { sortMenu = true })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LapSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                sort = option
                                sortMenu = false
                            },
                        )
                    }
                }
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        when {
            visible == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            visible.isEmpty() && all.isNullOrEmpty() -> SectionCard {
                Text("No laps yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Open Record, connect to GT7, then tap Capture this lap. Replays are optional " +
                        "and can be added from the lap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            visible.isEmpty() -> SectionCard {
                Text("No laps match this filter", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Clear the filter or capture another lap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible) { lap ->
                    LapCard(
                        lap = lap,
                        driver = lap.driverPsnId?.let { profiles[it.lowercase()] },
                        menuOpen = choosing == lap,
                        candidates = if (choosing == lap) choices else null,
                        onOpen = { onOpenLap(lap.sessionId, lap.lapIndex) },
                        onChooseTrack = { chooseTrack(lap) },
                        onPickTrack = { name ->
                            choosing = null
                            rename(lap, name)
                        },
                        onCustomName = {
                            renaming = lap
                            renameText = ""
                            choosing = null
                        },
                        onDismissMenu = { choosing = null },
                        onDelete = { deleting = lap },
                    )
                }
            }
        }
    }

    renaming?.let { lap ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Track name") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renaming ?: return@TextButton
                    renaming = null
                    rename(target, renameText.trim().ifBlank { TrackNamer.UNKNOWN })
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { lap ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete lap?") },
            text = { Text("${lap.trackLabel} · ${lapTimeLabel(lap.lapTimeMs)} and its replay will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleting ?: return@TextButton
                    deleting = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            store.deleteLap(target.sessionId, target.lapIndex)
                        }
                        if (!deleted) message = "Lap could not be deleted."
                        reload++
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LapCard(
    lap: LapSummary,
    driver: DriverProfile?,
    menuOpen: Boolean,
    candidates: List<TrackCandidate>?,
    onOpen: () -> Unit,
    onChooseTrack: () -> Unit,
    onPickTrack: (String) -> Unit,
    onCustomName: () -> Unit,
    onDismissMenu: () -> Unit,
    onDelete: () -> Unit,
) {
    val driverName = driver?.nickname ?: lap.driverPsnId
    val driverFlag = driver?.countryCode?.let(::countryFlagEmoji)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                NumericText(lapTimeLabel(lap.lapTimeMs), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${lap.trackLabel} · ${lap.carName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    driverFlag?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        driverName ?: "Driver not set",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (driverName == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatDate(lap.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (lap.hasReplay) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    if (lap.hasReplay) "Replay" else "No replay",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lap.hasReplay) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryPill("Open", onOpen, Modifier.weight(1f))
            TrackChoiceButton(
                unknown = lap.trackLabel == TrackNamer.UNKNOWN,
                expanded = menuOpen,
                candidates = candidates,
                onOpen = onChooseTrack,
                onDismiss = onDismissMenu,
                onPick = onPickTrack,
                onCustom = onCustomName,
            )
            GhostPill(
                "Delete",
                onDelete,
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DriverProfileHeader(
    profile: DriverProfile?,
    menuOpen: Boolean,
    onAdd: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onGridStats: () -> Unit,
    onGtshRank: () -> Unit,
    onEdit: () -> Unit,
) {
    if (profile == null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Your GT profile appears here",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostPill(
                text = "Add GT profile",
                onClick = onAdd,
                modifier = Modifier.testTag("library-add-profile"),
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        countryFlagEmoji(profile.countryCode)?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.nickname,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                profile.psnId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            GhostPill(
                text = "GT profile ▾",
                onClick = onOpenMenu,
                modifier = Modifier.testTag("library-profile-menu"),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("GT GridStats") }, onClick = onGridStats)
                DropdownMenuItem(text = { Text("GTSH-Rank") }, onClick = onGtshRank)
                DropdownMenuItem(text = { Text("Edit profile") }, onClick = onEdit)
            }
        }
    }
}

/** Rename for a named lap, or a candidate list for one identification could not settle. */
@Composable
internal fun TrackChoiceButton(
    unknown: Boolean,
    expanded: Boolean,
    candidates: List<TrackCandidate>?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCustom: () -> Unit,
) {
    Box {
        GhostPill(if (unknown) "Choose track" else "Rename", onOpen)
        if (!unknown) return@Box
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            val offered = candidates
            if (offered == null) {
                DropdownMenuItem(
                    text = { Text("Finding tracks…") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                offered.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.name) },
                        onClick = { onPick(candidate.name) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Enter a name…") },
                    onClick = onCustom,
                )
            }
        }
    }
}

private val LapSort.label: String
    get() = when (this) {
        LapSort.Newest -> "Newest"
        LapSort.Fastest -> "Fastest"
        LapSort.Track -> "Track"
    }
