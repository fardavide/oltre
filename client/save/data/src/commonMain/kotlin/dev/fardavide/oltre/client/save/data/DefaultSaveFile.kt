package dev.fardavide.oltre.client.save.data

// The save file in the platform's per-app data directory. Called once, at the composition root.
expect fun defaultSaveFile(): SaveFile

// The preferences file, beside the save in that same directory. A second file rather than a second
// key in the first one: what the app remembers about itself has nothing to do with what the
// simulation holds, and a preference must never be able to cost a player a colony — separate files
// mean a corrupt one of either kind takes only its own down. Called once, at the composition root.
expect fun defaultPreferencesFile(): SaveFile

// **The queue of taps the server has not answered, beside the save and the preferences.** A third
// file for the second file's reason, one asymmetry sharper: a corrupt save costs a colony the server
// still holds, and a corrupt outbox costs taps the player actually made — see `Outbox.queued`, which
// has no honest way to recover half a queue. Neither may be able to take the other down.
expect fun defaultOutboxFile(): SaveFile

// **The session, and it is deliberately not the save.** `SessionStore`'s own KDoc is the argument:
// a corrupt save costs a colony, a corrupt session costs a sign-in — which is a screen the player can
// answer. Called once, at the composition root.
expect fun defaultSessionFile(): SaveFile

internal const val SAVE_FILE_NAME = "colony.json"

internal const val OUTBOX_FILE_NAME = "outbox.json"

internal const val SESSION_FILE_NAME = "session.json"

internal const val PREFERENCES_FILE_NAME = "preferences.json"

// Desktop writes into a shared user directory and so needs a folder of its own; the mobile
// platforms hand out a private per-app directory already.
internal const val SAVE_DIRECTORY_NAME = "Oltre"
