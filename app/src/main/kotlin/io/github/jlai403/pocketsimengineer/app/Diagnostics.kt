package io.github.jlai403.pocketsimengineer.app

import android.content.Context
import java.io.File
import java.time.Instant

/** Append-only timeline that survives logcat rotation, so "why did it not stop" is answerable. */
object Diagnostics {
    private const val MAX_BYTES = 256 * 1024
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "diagnostics.log")
    }

    @Synchronized
    fun log(message: String) {
        val target = file ?: return
        runCatching {
            if (target.length() > MAX_BYTES) {
                val tail = target.readLines().takeLast(500)
                target.writeText(tail.joinToString("\n", postfix = "\n"))
            }
            target.appendText("${Instant.now()} $message\n")
        }
    }

    fun tail(context: Context, lines: Int = 40): List<String> =
        runCatching { File(context.filesDir, "diagnostics.log").readLines().takeLast(lines) }
            .getOrDefault(emptyList())
}
