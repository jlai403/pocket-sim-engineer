package io.github.jlai403.lapcraft.app.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.jlai403.lapcraft.core.session.CsvExporter
import io.github.jlai403.lapcraft.core.session.LapMetadata
import io.github.jlai403.lapcraft.core.session.SessionStore
import java.io.File

object Sharing {
    fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun exportCsv(
        context: Context,
        store: SessionStore,
        sessionId: String,
        lap: LapMetadata,
    ): File {
        val samples = store.readSamples(sessionId, lap.index)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "%s-lap-%03d.csv".format(sessionId, lap.index))
        file.writeText(CsvExporter.export(samples))
        return file
    }
}
