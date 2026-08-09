package dev.fardavide.oltre.client.save.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Identical to the copy in desktopMain — the two collapse into one shared JVM source set the day
// the Android app module lands and both targets can agree on where the directory comes from.
internal class FileSaveFile(private val file: File) : SaveFile {

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            if (file.isFile) file.readText() else null
        } catch (e: IOException) {
            null
        }
    }

    // Written to a sibling and moved into place: a process killed mid-write leaves the previous
    // colony intact instead of a half-file that decodes to nothing.
    override suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            try {
                file.parentFile?.mkdirs()
                val temporary = File(file.parentFile, "${file.name}.tmp")
                temporary.writeText(text)
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                // Best effort, per SaveFile.write.
            }
        }
    }

    // The temporary sibling goes too. `write` moves it into place, so one is only ever left behind
    // by a process killed mid-write — and a reset that left it would hand the next write a stale
    // file to overwrite rather than a clean directory.
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                file.delete()
                File(file.parentFile, "${file.name}.tmp").delete()
            } catch (e: SecurityException) {
                // Best effort, per SaveFile.clear.
            }
        }
    }
}
