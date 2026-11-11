package io.github.jlai403.pocketsimengineer.app.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import io.github.jlai403.pocketsimengineer.BuildConfig
import io.github.jlai403.pocketsimengineer.core.update.UpdateJsonCodec
import io.github.jlai403.pocketsimengineer.core.update.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Checks the rolling `debug-latest` release for a newer build, downloads it,
 * and hands it to the system installer. Android always asks the user before
 * replacing the app; nothing here can install silently.
 */
object UpdateManager {
    private const val PREVIEW_MANIFEST =
        "https://github.com/jlai403/pocket-sim-engineer/releases/download/preview-latest/latest.json"
    private const val STABLE_MANIFEST =
        "https://github.com/jlai403/pocket-sim-engineer/releases/latest/download/latest.json"
    const val ACTION_INSTALL_STATUS =
        "io.github.jlai403.pocketsimengineer.action.INSTALL_STATUS"

    private const val PREFS = "updates"
    private const val LAST_CHECK = "last_check_ms"
    private const val STABLE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val APK_NAME = "update.apk"
    private const val STORE_FDROID = "org.fdroid.fdroid"
    private const val STORE_PLAY = "com.android.vending"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val manifest: UpdateManifest) : State
        data class Downloading(val manifest: UpdateManifest, val percent: Int) : State
        data class Ready(val manifest: UpdateManifest, val file: File) : State
        data class Installing(val manifest: UpdateManifest) : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun checkIfStale(context: Context) {
        val app = context.applicationContext
        if (!supportedInstallSource(app)) return
        val last = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_CHECK, 0)
        val interval = if (BuildConfig.UPDATE_CHANNEL == "stable") STABLE_CHECK_INTERVAL_MS else 0L
        if (System.currentTimeMillis() - last < interval) return
        if (state.value !is State.Idle && state.value !is State.UpToDate) return
        check(app)
    }

    fun check(context: Context) {
        val app = context.applicationContext
        if (!supportedInstallSource(app)) return
        if (state.value is State.Checking ||
            state.value is State.Downloading ||
            state.value is State.Installing
        ) {
            return
        }
        state.value = State.Checking
        scope.launch {
            val manifest = runCatching { fetchManifest() }
                .getOrElse {
                    state.value = State.Failed("Update check failed: ${it.message}")
                    return@launch
                }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_CHECK, System.currentTimeMillis())
                .apply()
            state.value = if (manifest.isNewerThan(BuildConfig.VERSION_CODE)) {
                State.Available(manifest)
            } else {
                State.UpToDate
            }
        }
    }

    fun download(context: Context) {
        val app = context.applicationContext
        val manifest = when (val current = state.value) {
            is State.Available -> current.manifest
            is State.Failed -> return
            else -> return
        }
        state.value = State.Downloading(manifest, 0)
        scope.launch {
            runCatching {
                val file = File(app.cacheDir, APK_NAME)
                fetchApk(manifest, file) { percent ->
                    state.value = State.Downloading(manifest, percent)
                }
                if (manifest.sha256.isNotBlank()) {
                    check(sha256(file).equals(manifest.sha256, ignoreCase = true)) {
                        "Checksum mismatch"
                    }
                }
                file
            }.onSuccess { file ->
                state.value = State.Ready(manifest, file)
            }.onFailure { error ->
                state.value = State.Failed("Download failed: ${error.message}")
            }
        }
    }

    fun install(context: Context) {
        val app = context.applicationContext
        val ready = state.value as? State.Ready ?: return
        if (!app.packageManager.canRequestPackageInstalls()) {
            openInstallPermission(app)
            state.value = State.Failed("Allow installs from this app, then tap Install again.")
            return
        }
        state.value = State.Installing(ready.manifest)
        scope.launch {
            runCatching {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                )
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, ready.file.length()).use { output ->
                        ready.file.inputStream().use { input -> input.copyTo(output) }
                        session.fsync(output)
                    }
                    val status = Intent(app, UpdateInstallReceiver::class.java)
                        .setAction(ACTION_INSTALL_STATUS)
                    val pending = PendingIntent.getBroadcast(
                        app,
                        sessionId,
                        status,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
            }.onFailure { error ->
                state.value = State.Failed("Install failed: ${error.message}")
            }
        }
    }

    fun onInstallResult(success: Boolean, message: String?) {
        state.value = if (success) State.Idle else State.Failed("Install failed: $message")
    }

    private fun fetchManifest(): UpdateManifest {
        val connection = open(manifestUrl())
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return UpdateJsonCodec.decode(text) ?: error("Unreadable update manifest")
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchApk(manifest: UpdateManifest, target: File, onProgress: (Int) -> Unit) {
        val url = manifestUrl().substringBeforeLast('/') + "/" + manifest.apk
        val connection = open(url)
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong
            var read = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun manifestUrl(): String =
        if (BuildConfig.UPDATE_CHANNEL == "stable") STABLE_MANIFEST else PREVIEW_MANIFEST

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun supportedInstallSource(context: Context): Boolean = runCatching {
        val installer = context.packageManager
            .getInstallSourceInfo(context.packageName)
            .installingPackageName
        installer != STORE_FDROID && installer != STORE_PLAY
    }.getOrDefault(true)

    private fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
