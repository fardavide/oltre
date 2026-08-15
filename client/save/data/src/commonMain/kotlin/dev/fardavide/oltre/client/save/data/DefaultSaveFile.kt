package dev.fardavide.oltre.client.save.data

// The save file in the platform's per-app data directory. Called once, at the composition root.
expect fun defaultSaveFile(): SaveFile

// The preferences file, beside the save in that same directory. A second file rather than a second
// key in the first one: what the app remembers about itself has nothing to do with what the
// simulation holds, and a preference must never be able to cost a player a colony — separate files
// mean a corrupt one of either kind takes only its own down. Called once, at the composition root.
expect fun defaultPreferencesFile(): SaveFile

internal const val SAVE_FILE_NAME = "colony.json"

internal const val PREFERENCES_FILE_NAME = "preferences.json"

// Desktop writes into a shared user directory and so needs a folder of its own; the mobile
// platforms hand out a private per-app directory already.
internal const val SAVE_DIRECTORY_NAME = "Oltre"
