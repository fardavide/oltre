package dev.fardavide.oltre.client.save.data

import java.io.File

// Android has no context-free way to name an app's private directory, and there is no Android
// app module yet (see `.claude/docs/architecture.md`). The application sets this once at
// startup — `AndroidSaveLocation.directory = context.filesDir` — and this file goes away when
// that module lands and can supply it properly.
object AndroidSaveLocation {
    var directory: File? = null
}

actual fun defaultSaveFile(): SaveFile = FileSaveFile(File(androidDataDirectory(), SAVE_FILE_NAME))

actual fun defaultPreferencesFile(): SaveFile = FileSaveFile(File(androidDataDirectory(), PREFERENCES_FILE_NAME))

actual fun defaultOutboxFile(): SaveFile = FileSaveFile(File(androidDataDirectory(), OUTBOX_FILE_NAME))

actual fun defaultSessionFile(): SaveFile = FileSaveFile(File(androidDataDirectory(), SESSION_FILE_NAME))

// Read at every call rather than resolved once into a property, so the requirement stays a
// requirement: a caller that asks for a file before the application has set the directory is told
// so, instead of being handed whatever the class initialiser happened to see.
private fun androidDataDirectory(): File = requireNotNull(AndroidSaveLocation.directory) {
    "AndroidSaveLocation.directory must be set to context.filesDir before the first file is asked for"
}
