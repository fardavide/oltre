package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.SessionResponse

// **Where the session lives between launches**, and the port is `OutboxFile`'s shape for
// `OutboxFile`'s reason: `:client:save:data` already writes bytes to a platform directory, and
// reusing its interface would mean a `:client:net:data -> :client:save:data` edge, which the build
// warns about on every clean run so that features do not reach through each other. The composition
// root is the one module allowed to see both.
//
// **It holds the whole `SessionResponse` rather than the two tokens**, and the expiries are the
// reason. A client that stored only the strings would have to decode a JWT to know when to refresh —
// which means trusting a body it is not the one that signed — and `SessionResponse` puts both
// instants on the wire precisely so that it never has to.
//
// **It is not the save file and must never become it.** A corrupt save costs a colony that the
// server still holds; a corrupt session costs a sign-in, which is a screen the player can answer.
// Keeping them apart is what stops one taking the other down, and it is `PreferencesStore`'s
// argument made a third time.
interface SessionStore {

    // Null when nobody has signed in on this device, and also when what is there cannot be read.
    // Those are one answer because there is one thing to do about them — show the gate — and a
    // session that cannot be parsed is a session that cannot be sent.
    suspend fun read(): SessionResponse?

    // Best effort, and the consequence is milder than the outbox's: a pair that fails to write costs
    // the player a sign-in on the next cold launch, where an unwritten tap is a tap that never
    // happened. Still nothing to report it to.
    suspend fun write(session: SessionResponse)

    // What deleting an account and running out of refresh both do. Idempotent, so signing out of a
    // device that was never signed in is not an error.
    suspend fun clear()
}
