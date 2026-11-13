package io.github.jlai403.lapcraft.app.replay

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the PlayStation App, or its store listing when it is not installed. */
object PlayStationApp {
    const val PACKAGE = "com.scee.psxandroid"

    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch(context, intent)) return true
        return launch(
            context,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun launch(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess
}
