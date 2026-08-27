package dev.fardavide.oltre.client.save.data

import java.io.File
import java.util.Locale

// Desktop is only the dev loop, but a save nobody can find is a save nobody can delete, so it
// lands in the platform's conventional per-app data directory rather than beside the jar.
actual fun defaultSaveFile(): SaveFile = FileSaveFile(File(desktopDataDirectory(), SAVE_FILE_NAME))

actual fun defaultPreferencesFile(): SaveFile = FileSaveFile(File(desktopDataDirectory(), PREFERENCES_FILE_NAME))

actual fun defaultOutboxFile(): SaveFile = FileSaveFile(File(desktopDataDirectory(), OUTBOX_FILE_NAME))

actual fun defaultSessionFile(): SaveFile = FileSaveFile(File(desktopDataDirectory(), SESSION_FILE_NAME))

private fun desktopDataDirectory(): File {
    val operatingSystem = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    val home = File(System.getProperty("user.home").orEmpty())
    return when {
        operatingSystem.contains("mac") -> File(home, "Library/Application Support/$SAVE_DIRECTORY_NAME")
        operatingSystem.contains("win") -> File(System.getenv("APPDATA") ?: home.path, SAVE_DIRECTORY_NAME)
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, SAVE_DIRECTORY_NAME)
    }
}
