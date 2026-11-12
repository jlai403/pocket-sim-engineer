package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfile
import io.github.jlai403.pocketsimengineer.app.profile.DriverProfileStore
import io.github.jlai403.pocketsimengineer.app.profile.GridStatsProfileClient
import io.github.jlai403.pocketsimengineer.app.profile.countryFlagEmoji
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DriverSection(
    sessionId: String,
    lapIndex: Int,
    driverPsnId: String?,
    onChanged: () -> Unit,
    lookupProfile: suspend (String) -> DriverProfile = { GridStatsProfileClient().fetch(it) },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStore = remember { SessionStore(context.filesDir) }
    val profileStore = remember { DriverProfileStore(context) }
    val owner = remember { profileStore.owner() }
    var profiles by remember { mutableStateOf(profileStore.profiles()) }
    var menuOpen by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val driver = driverPsnId?.let(profileStore::profile)

    fun assign(profile: DriverProfile?) {
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                sessionStore.setLapDriver(sessionId, lapIndex, profile?.psnId)
            }
            if (updated == null) {
                error = "Driver could not be saved."
                return@launch
            }
            profile?.let {
                profileStore.save(it)
                profiles = profileStore.profiles()
            }
            error = null
            onChanged()
        }
    }

    SectionCard(title = "Driver") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            countryFlagEmoji(driver?.countryCode)?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driver?.nickname ?: driverPsnId ?: "No driver",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    driver?.psnId ?: driverPsnId ?: "Defaults to your profile when set",
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
                                assign(option)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Add driver…") },
                        onClick = {
                            menuOpen = false
                            adding = true
                        },
                    )
                    if (driverPsnId != null) {
                        DropdownMenuItem(
                            text = { Text("Clear driver") },
                            onClick = {
                                menuOpen = false
                                assign(null)
                            },
                        )
                    }
                }
            }
        }
        if (driverPsnId == null && owner != null) {
            GhostPill(
                text = "Use my profile",
                onClick = { assign(owner) },
                modifier = Modifier.testTag("driver-use-owner"),
            )
        }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (adding) {
        AddDriverDialog(
            lookupProfile = lookupProfile,
            onSave = {
                adding = false
                assign(it)
            },
            onDismiss = { adding = false },
        )
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

@Composable
internal fun AddDriverDialog(
    lookupProfile: suspend (String) -> DriverProfile,
    onSave: (DriverProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var psnId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fetch() {
        val target = psnId.trim()
        if (target.isEmpty()) {
            error = "Enter a PSN ID."
            return
        }
        if (fetching) return
        fetching = true
        error = null
        scope.launch {
            try {
                onSave(lookupProfile(target))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Profile lookup failed."
                manual = true
            } finally {
                fetching = false
            }
        }
    }

    fun saveManual() {
        val target = psnId.trim()
        if (target.isEmpty()) {
            error = "Enter a PSN ID."
            return
        }
        val code = countryCode.trim().uppercase()
        if (code.isNotEmpty() && (code.length != 2 || code.any { it !in 'A'..'Z' })) {
            error = "Use a 2-letter country code, e.g. GB."
            return
        }
        onSave(
            DriverProfile(
                psnId = target,
                nickname = name.trim().ifEmpty { target },
                countryCode = code.ifEmpty { null },
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add driver") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = psnId,
                    onValueChange = {
                        psnId = it
                        error = null
                    },
                    label = { Text("PSN ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("driver-add-psn"),
                    shape = RoundedCornerShape(14.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GhostPill(
                        text = if (fetching) "Fetching…" else "Fetch profile",
                        onClick = { fetch() },
                        enabled = !fetching,
                        modifier = Modifier.testTag("driver-add-fetch"),
                    )
                    TextButton(
                        onClick = { manual = true },
                        modifier = Modifier.testTag("driver-add-manual"),
                    ) {
                        Text("Enter manually")
                    }
                }
                if (manual) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            error = null
                        },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("driver-add-name"),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = {
                                countryCode = it.uppercase().take(2)
                                error = null
                            },
                            label = { Text("Country code") },
                            placeholder = { Text("GB") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("driver-add-country"),
                            shape = RoundedCornerShape(14.dp),
                        )
                        countryFlagEmoji(countryCode)?.let {
                            Text(it, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    PrimaryPill(
                        text = "Save driver",
                        onClick = { saveManual() },
                        modifier = Modifier.fillMaxWidth().testTag("driver-add-save"),
                    )
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
