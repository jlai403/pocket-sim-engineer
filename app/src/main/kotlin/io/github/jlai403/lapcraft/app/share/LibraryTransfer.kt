package io.github.jlai403.lapcraft.app.share

import android.content.Context
import android.net.Uri
import io.github.jlai403.lapcraft.core.session.ExportReport
import io.github.jlai403.lapcraft.core.session.ImportReport
import io.github.jlai403.lapcraft.core.session.LapRef
import io.github.jlai403.lapcraft.core.session.LibraryArchive
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Moves library archives between app storage and a SAF document the user picked. */
object LibraryTransfer {
    suspend fun export(
        context: Context,
        uri: Uri,
        rootDir: File,
        laps: List<LapRef>,
        onProgress: (Long, Long) -> Unit,
    ): ExportReport = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: error("Could not open the chosen file")
        out.use { LibraryArchive.export(rootDir, laps, it, onProgress) }
    }

    suspend fun import(
        context: Context,
        uri: Uri,
        rootDir: File,
        onProgress: (Long, Long) -> Unit,
    ): ImportReport = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not read the chosen file")
        input.use { LibraryArchive.import(rootDir, it, onProgress) }
    }
}
