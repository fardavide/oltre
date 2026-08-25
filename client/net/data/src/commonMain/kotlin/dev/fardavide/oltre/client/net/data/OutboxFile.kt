package dev.fardavide.oltre.client.net.data

// The impure edge of the outbox: one named blob of text, nothing game-shaped. Everything above it
// is pure, so tests swap in an in-memory implementation and never touch a filesystem.
//
// **This is `SaveFile` with a different name, and that is deliberate rather than an oversight.**
// `:client:save:data` already declares that interface and already has a platform implementation of
// it, and reusing it would mean a `:client:net:data -> :client:save:data` edge — a cross-feature
// dependency, which the build warns about on every clean run precisely so that features do not
// start reaching through each other. So the outbox declares the port it needs and the composition
// root, which is the one module allowed to see both, supplies something that writes bytes. That
// costs an adapter of about five lines at `#113` and buys the property the warning defends.
interface OutboxFile {

    // Null when nothing has been queued yet, and also when the bytes cannot be read at all.
    suspend fun read(): String?

    // Best effort in the same sense `SaveFile.write` is, and with a sharper consequence worth
    // stating: an unwritable outbox loses the tap that could not be written, where an unwritable
    // save loses nothing that the next write does not put back. There is still no surface to
    // report it to and crashing a player mid-session over a full disk is worse, so the honest
    // position is that this is the one place where a disk failure costs something.
    suspend fun write(text: String)

    // Leave nothing behind, so the next `read` answers null. What an emptied outbox does rather
    // than writing `[]` — a drained queue and a queue that never existed are the same thing, and
    // two ways to say it is one way too many. Idempotent.
    suspend fun clear()
}
