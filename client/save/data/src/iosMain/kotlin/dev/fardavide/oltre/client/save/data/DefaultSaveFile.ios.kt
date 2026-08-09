package dev.fardavide.oltre.client.save.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
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

    // No temporary sibling to sweep up, unlike the JVM copies: `atomically` is Foundation's own
    // write-then-move and it cleans up after itself.
    //
    // The opt-in is for the `error` parameter and nothing else: Foundation's out-parameter is a
    // `CPointer`, so passing even `null` to it is cinterop. It is the only line in this file that
    // touches one — `read` and `write` go through NSData, which needs no pointer.
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun clear() {
        withContext(Dispatchers.Default) {
            // The error is discarded rather than inspected, per SaveFile.clear — and the commonest
            // one is "no such file", which is a reset that has already achieved what it was for.
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}
