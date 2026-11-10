package io.github.jlai403.pocketsimengineer.app

import android.app.Application
import io.github.jlai403.pocketsimengineer.app.recording.RecordingService
import io.github.jlai403.pocketsimengineer.app.recording.SessionFinalizer
import io.github.jlai403.pocketsimengineer.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PocketSimEngineerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val keepRaw = getSharedPreferences(RecordingService.SETTINGS, MODE_PRIVATE)
            .getBoolean(RecordingService.KEEP_RAW, false)
        appScope.launch {
            SessionFinalizer.recover(SessionStore(filesDir), keepRaw)
        }
    }

    companion object {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
