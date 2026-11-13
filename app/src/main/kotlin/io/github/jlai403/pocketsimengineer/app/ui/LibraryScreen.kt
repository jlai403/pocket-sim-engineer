package io.github.jlai403.pocketsimengineer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileLinks
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.app.profile.countryFlagEmoji
import io.github.jlai403.pocketsimengineer.app.replay.LapReencoder
import io.github.jlai403.pocketsimengineer.app.replay.PlayStationApp
import io.github.jlai403.pocketsimengineer.app.replay.VideoTranscoder
import io.github.jlai403.pocketsimengineer.app.settings.SettingsStore
import io.github.jlai403.pocketsimengineer.app.track.TrackCatalog
import io.github.jlai403.pocketsimengineer.app.track.TrackNamer
import io.github.jlai403.pocketsimengineer.core.session.LapFilter
import io.github.jlai403.pocketsimengineer.core.session.LapSort
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import io.github.jlai403.pocketsimengineer.core.session.applyFilter
import io.github.jlai403.pocketsimengineer.core.session.applySort
import io.github.jlai403.pocketsimengineer.core.session.fastestPerTrack
import io.github.jlai403.pocketsimengineer.core.session.sameLap
import io.github.jlai403.pocketsimengineer.core.session.trackKey
import io.github.jlai403.pocketsimengineer.core.track.TrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenLap: (String, Int) -> Unit,
    onOpenRecord: () -> Unit = {},
    onOpenSync: (String, Int, String?) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SessionStore(context.filesDir) }
    val settings = remember { SettingsStore(context) }
    val profileStore = remember { DriverProfileStore(context) }
    val namer = remember { TrackNamer(store, TrackCatalog(context)) }
    val owner = remember { profileStore.owner() }
    var profiles by remember {
        mutableStateOf(profileStore.profiles().associateBy { it.psnId.lowercase() })
    }
    var laps by remember { mutableStateOf<List<LapSummary>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(LapFilter()) }
    var sort by remember { mutableStateOf(LapSort.Newest) }
    var previewEmpty by remember { mutableStateOf(settings.previewEmptyLibrary) }
    var editing by remember { mutableStateOf<LapSummary?>(null) }
    var candidates by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var deleting by remember { mutableStateOf<LapSummary?>(null) }
    var syncTarget by remember { mutableStateOf<LapSummary?>(null) }
    var reencoding by remember { mutableStateOf(false) }
    var reencodeResult by remember { mutableStateOf<Boolean?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var trackMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = syncTarget
        syncTarget = null
        if (uri != null && target != null) {
            onOpenSync(target.sessionId, target.lapIndex, uri.toString())
        }
    }

    LaunchedEffect(reload) {
        if (settings.trackIdentificationVersion < TrackNamer.VERSION) {
            runCatching { namer.identifyUnknownLaps() }
            settings.trackIdentificationVersion = TrackNamer.VERSION
        }
        laps = withContext(Dispatchers.IO) { store.summaries() }
        refreshing = false
    }

    fun openLink(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) {
            message = "No app can open that link."
        }
    }

    fun edit(lap: LapSummary) {
        editing = lap
        candidates = null
        reencodeResult = null
        if (lap.trackLabel == TrackNamer.UNKNOWN) {
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    runCatching { namer.candidates(lap.sessionId, lap.lapIndex) }.getOrDefault(emptyList())
                }
                if (editing?.sameLap(lap) == true) candidates = found
            }
        }
    }

    fun saveTrack(lap: LapSummary, name: String) {
        scope.launch {
            val renamed = withContext(Dispatchers.IO) {
                runCatching { store.rename(lap.sessionId, name) }.getOrNull()
            }
            if (renamed == null) message = "Track name could not be saved."
            reload++
        }
    }

    fun assignDriver(lap: LapSummary, profile: DriverProfile?) {
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                runCatching { store.setLapDriver(lap.sessionId, lap.lapIndex, profile?.psnId) }.getOrNull()
            }
            if (updated == null) {
                message = "Driver could not be saved."
                return@launch
            }
            profile?.let {
                profileStore.save(it)
                profiles = profileStore.profiles().associateBy { saved -> saved.psnId.lowercase() }
            }
            reload++
        }
    }

    fun reencode(lap: LapSummary) {
        if (reencoding) return
        reencoding = true
        reencodeResult = null
        scope.launch {
            val reencoded = runCatching {
                LapReencoder(store, VideoTranscoder(context)).reencode(lap.sessionId, lap.lapIndex)
            }.getOrDefault(false)
            reencoding = false
            reencodeResult = reencoded
            if (reencoded) {
                val fresh = withContext(Dispatchers.IO) {
                    store.summaries().firstOrNull { it.sameLap(lap) }
                }
                if (fresh != null && editing?.sameLap(lap) == true) editing = fresh
            }
            reload++
        }
    }

    val all = laps
    val visible = all?.applyFilter(filter)?.applySort(sort)
    val tracks = all?.distinctBy { it.trackKey }?.map { it.trackLabel }?.sorted().orEmpty()
    val rankable = all?.filter { it.trackLabel != TrackNamer.UNKNOWN }
    val fastest = rankable?.fastestPerTrack().orEmpty()
    val trackCounts = rankable?.groupingBy { it.trackKey }?.eachCount().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Library",
            subtitle = summaryLine(all, tracks, previewEmpty),
            modifier = Modifier.padding(horizontal = 20.dp),
            actions = {
                DriverChip(
                    profile = owner,
                    menuOpen = profileMenu,
                    onAdd = onOpenSettings,
                    onOpenMenu = { profileMenu = true },
                    onDismissMenu = { profileMenu = false },
                    onGridStats = {
                        profileMenu = false
                        owner?.let { openLink(DriverProfileLinks.gridStats(it.psnId)) }
                    },
                    onGtshRank = {
                        profileMenu = false
                        owner?.let { openLink(DriverProfileLinks.gtshRank(it.psnId)) }
                    },
                    onEdit = {
                        profileMenu = false
                        onOpenSettings()
                    },
                )
            },
        )

        if (!previewEmpty && !all.isNullOrEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    GhostPill(
                        text = filter.trackLabel ?: "All tracks",
                        onClick = { trackMenu = true },
                        trailingIcon = Icons.Filled.KeyboardArrowDown,
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
                    GhostPill(
                        text = sort.label,
                        onClick = { sortMenu = true },
                        trailingIcon = Icons.Filled.KeyboardArrowDown,
                    )
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
                if (filter != LapFilter()) {
                    TextButton(onClick = { filter = LapFilter() }) { Text("Clear") }
                }
            }

            SegmentedChoice(
                options = listOf("All", "Replay", "No replay"),
                selectedIndex = when (filter.hasReplay) {
                    null -> 0
                    true -> 1
                    false -> 2
                },
                modifier = Modifier.padding(horizontal = 20.dp),
                onSelect = { index ->
                    filter = filter.copy(
                        hasReplay = when (index) {
                            0 -> null
                            1 -> true
                            else -> false
                        },
                    )
                },
            )
        }

        message?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                reload++
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("lap-list"),
            ) {
                if (previewEmpty) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            PreviewBanner(
                                onTurnOff = {
                                    previewEmpty = false
                                    settings.previewEmptyLibrary = false
                                },
                            )
                        }
                    }
                }
                when {
                    all == null -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    previewEmpty || all.isEmpty() -> item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            EmptyLibrary(onOpenRecord)
                        }
                    }

                    visible.isNullOrEmpty() -> item {
                        SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Text("No laps match this filter", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Clear the filter or capture another lap.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            GhostPill("Clear filters", onClick = { filter = LapFilter() })
                        }
                    }

                    else -> itemsIndexed(
                        items = visible,
                        key = { _, lap -> "${lap.sessionId}:${lap.lapIndex}" },
                    ) { index, lap ->
                        val fastestLap = fastest[lap.trackKey]
                        val ranked = (trackCounts[lap.trackKey] ?: 0) >= 2
                        val isFastest = ranked && fastestLap?.sameLap(lap) == true
                        val deltaMs = if (ranked && !isFastest && fastestLap != null) {
                            lap.lapTimeMs - fastestLap.lapTimeMs
                        } else {
                            null
                        }
                        val driverPsn = lap.driverPsnId
                        val driver = driverPsn?.let { psn ->
                            profiles[psn.lowercase()] ?: owner?.takeIf { it.psnId.equals(psn, true) }
                        }
                        Column(modifier = Modifier.animateItem()) {
                            LapCard(
                                lap = lap,
                                driver = driver,
                                fastest = isFastest,
                                deltaMs = deltaMs,
                                onOpen = { onOpenLap(lap.sessionId, lap.lapIndex) },
                                onEdit = { edit(lap) },
                                onDelete = { deleting = lap },
                            )
                            if (index != visible.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.testTag("lap-divider"),
                                    thickness = 1.dp,
                                    color = Hairline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { lap ->
        EditLapSheet(
            lap = lap,
            driver = lap.driverPsnId?.let { profiles[it.lowercase()] },
            owner = owner,
            profiles = profiles.values.toList(),
            candidates = candidates,
            onDismiss = {
                editing = null
                reencodeResult = null
            },
            onSaveTrack = { name ->
                editing = null
                saveTrack(lap, name)
            },
            onAssignDriver = { profile -> assignDriver(lap, profile) },
            onAddReplay = {
                syncTarget = lap
                picker.launch(arrayOf("video/*"))
            },
            onOpenPlayStation = {
                if (!PlayStationApp.open(context)) message = "PlayStation App is not available."
            },
            onResumeSync = { onOpenSync(lap.sessionId, lap.lapIndex, null) },
            developerTools = settings.developerTools,
            reencoding = reencoding,
            reencodeResult = reencodeResult,
            onReencode = { reencode(lap) },
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
private fun EmptyLibrary(onOpenRecord: () -> Unit) {
    SectionCard {
        Text("No laps yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Connect to GT7 on the Record tab to start capturing a lap. " +
                "A replay can be added later to the recorded lap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryPill(
            text = "Go to Record",
            onClick = onOpenRecord,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewBanner(onTurnOff: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Previewing the empty library",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onTurnOff) { Text("Turn off") }
    }
}

@Composable
private fun DriverChip(
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
        GhostPill(
            text = "Add GT profile",
            onClick = onAdd,
            modifier = Modifier.testTag("library-add-profile"),
        )
        return
    }
    Box {
        val flag = countryFlagEmoji(profile.countryCode).orEmpty()
        GhostPill(
            text = listOf(flag, profile.nickname).filter { it.isNotEmpty() }.joinToString(" ") + " ▾",
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

private fun summaryLine(
    laps: List<LapSummary>?,
    tracks: List<String>,
    previewEmpty: Boolean,
): String? {
    if (laps.isNullOrEmpty() || previewEmpty) return null
    val best = laps.minOfOrNull { it.lapTimeMs }
    return buildString {
        append(laps.size)
        append(if (laps.size == 1) " lap · " else " laps · ")
        append(tracks.size)
        append(if (tracks.size == 1) " track" else " tracks")
        best?.let { append(" · Best ${lapTimeLabel(it)}") }
    }
}

private val LapSort.label: String
    get() = when (this) {
        LapSort.Newest -> "Newest"
        LapSort.Fastest -> "Fastest"
        LapSort.Track -> "Track"
    }
