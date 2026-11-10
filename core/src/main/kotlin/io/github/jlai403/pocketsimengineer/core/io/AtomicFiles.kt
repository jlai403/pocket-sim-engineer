package io.github.jlai403.pocketsimengineer.core.io

import java.io.File

/** Writes through a sibling temp file so a process death cannot leave a truncated target. */
object AtomicFiles {
    fun writeText(file: File, text: String) = writeBytes(file, text.toByteArray())

    fun writeBytes(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "could not replace ${file.path}" }
        }
    }
}
