package dev.kalpane.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

fun hash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Writes to a temp file, fsyncs, then atomically replaces the target. */
fun writeAtomic(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, ".${file.name}.writing")
    FileOutputStream(temp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

private val forbidden = Regex("""[/\\:*?"<>|\p{Cntrl}#\[\]]""")

/** A file-system and Git friendly name that keeps Unicode (including Kannada). */
fun safeName(input: String, fallback: String = "Untitled"): String {
    val cleaned = input.replace(forbidden, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimStart('.')
        .take(80)
        .trim()
    return cleaned.ifEmpty { fallback }
}

/** Returns [base], or "base 2", "base 3", ... so that [exists] is false. */
fun uniqueName(base: String, exists: (String) -> Boolean): String {
    if (!exists(base)) return base
    var n = 2
    while (exists("$base $n")) n++
    return "$base $n"
}
