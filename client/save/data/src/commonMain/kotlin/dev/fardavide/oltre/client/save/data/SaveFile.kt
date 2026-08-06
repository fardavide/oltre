package dev.fardavide.oltre.client.save.data

// The impure edge of persistence: one named blob of text, nothing game-shaped. Everything above
// it is pure, so tests swap in an in-memory implementation and never touch a filesystem.
interface SaveFile {

    // Null when nothing has been saved yet, and also when the bytes cannot be read at all — a
    // player with an unreadable file gets a new colony, never a crash on launch.
    suspend fun read(): String?

    // Best effort. There is no surface in the game to report a failed write to, and the next
    // event writes the whole snapshot again, so an unwritable file is dropped rather than
    // thrown: the alternative is crashing a player mid-session over a full disk.
    suspend fun write(text: String)
}
