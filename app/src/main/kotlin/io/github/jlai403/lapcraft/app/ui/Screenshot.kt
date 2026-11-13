package io.github.jlai403.lapcraft.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import io.github.jlai403.lapcraft.app.share.Sharing
import java.io.File

/** Captures the current window (video frame included) and shares it as a PNG. */
internal fun shareScreenshot(context: Context, title: String, onError: (String) -> Unit) {
    val activity = context.findActivity()
    if (activity == null) {
        onError("Screenshot is not available here.")
        return
    }
    val window = activity.window
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) {
        onError("Nothing to capture yet.")
        return
    }
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        window,
        bitmap,
        { result ->
            if (result != PixelCopy.SUCCESS) {
                onError("Screenshot failed.")
                return@request
            }
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "lap-%d.png".format(System.currentTimeMillis()))
            runCatching {
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }.onSuccess {
                Sharing.shareFile(context, file, "image/png", title)
            }.onFailure {
                onError("Screenshot failed: ${it.message}")
            }
        },
        Handler(Looper.getMainLooper()),
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
