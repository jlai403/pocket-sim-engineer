package io.github.jlai403.pocketsimengineer.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.core.session.LapSort
import io.github.jlai403.pocketsimengineer.core.session.LapSummary
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import io.github.jlai403.pocketsimengineer.core.session.applySort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shown after a share from the PS App: the user picks which lap this replay belongs to. */
@Composable
fun ImportPickScreen(
    uri: String,
    onPick: (String, Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SessionStore(context.filesDir) }
    var laps by remember { mutableStateOf<List<LapSummary>?>(null) }

    LaunchedEffect(uri) {
        laps = withContext(Dispatchers.IO) {
            store.summaries()
                .filter { !it.hasReplay && !it.replayPending }
                .applySort(LapSort.Newest)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "Choose a lap",
            subtitle = "Replay received · pick the lap it belongs to",
            actions = { GhostPill("Close", onClick = onClose) },
        )

        val data = laps
        when {
            data == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            data.isEmpty() -> SectionCard {
                Text("No laps without a replay", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Capture a lap first, or delete the replay already attached to a lap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GhostPill("Close", onClick = onClose)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(data) { lap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            NumericText(
                                lapTimeLabel(lap.lapTimeMs),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "${lap.trackLabel} · ${lap.carName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatDate(lap.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PrimaryPill(
                            text = "Attach",
                            onClick = { onPick(lap.sessionId, lap.lapIndex) },
                        )
                    }
                }
            }
        }
    }
}
