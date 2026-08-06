package dev.fardavide.oltre.client.save.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

// Documents, not Application Support: it is the one per-app directory iOS guarantees already
// exists, which keeps this free of directory creation and its error handling. The app does not
// enable file sharing, so the save stays invisible to the user and inside the iCloud backup.
actual fun defaultSaveFile(): SaveFile = IosSaveFile("${documentsDirectory()}/$SAVE_FILE_NAME")

private fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

private class IosSaveFile(private val path: String) : SaveFile {

    override suspend fun read(): String? = withContext(Dispatchers.Default) {
        val data = NSData.dataWithContentsOfFile(path) ?: return@withContext null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    // `atomically` is Foundation's own write-then-move, so the guarantee matches the JVM side:
    // a process killed mid-write leaves the previous colony intact.
    override suspend fun write(text: String) {
        withContext(Dispatchers.Default) {
            val data = (text as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            if (data != null) {
                data.writeToFile(path, atomically = true)
            }
        }
    }
}
