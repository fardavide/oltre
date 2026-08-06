package dev.fardavide.oltre.client.save.data

// The save file in the platform's per-app data directory. Called once, at the composition root.
expect fun defaultSaveFile(): SaveFile

internal const val SAVE_FILE_NAME = "colony.json"

// Desktop writes into a shared user directory and so needs a folder of its own; the mobile
// platforms hand out a private per-app directory already.
internal const val SAVE_DIRECTORY_NAME = "Oltre"
