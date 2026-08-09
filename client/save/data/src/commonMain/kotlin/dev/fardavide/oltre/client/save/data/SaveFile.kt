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

    // Leave nothing behind, so the next `read` answers null and the game starts a new colony. What
    // the debug menu's reset is built on, and the reason it deletes rather than writing a fresh
    // snapshot over the top: a fresh colony needs a galaxy seed, core cannot mint one, and the
    // composition root already mints exactly that on a first launch. Deleting turns a reset into a
    // first launch instead of a second way of building one.
    //
    // Best effort, like write, and idempotent: clearing a file that is not there has already
    // achieved what it was asked for.
    suspend fun clear()
}
