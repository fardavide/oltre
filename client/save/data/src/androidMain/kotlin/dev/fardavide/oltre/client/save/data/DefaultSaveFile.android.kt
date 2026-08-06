package dev.fardavide.oltre.client.save.data

import java.io.File

// Android has no context-free way to name an app's private directory, and there is no Android
// app module yet (see `.claude/docs/architecture.md`). The application sets this once at
// startup — `AndroidSaveLocation.directory = context.filesDir` — and this file goes away when
// that module lands and can supply it properly.
object AndroidSaveLocation {
    var directory: File? = null
}

actual fun defaultSaveFile(): SaveFile = FileSaveFile(
    File(
        requireNotNull(AndroidSaveLocation.directory) {
            "AndroidSaveLocation.directory must be set to context.filesDir before the first save"
        },
        SAVE_FILE_NAME,
    ),
)
