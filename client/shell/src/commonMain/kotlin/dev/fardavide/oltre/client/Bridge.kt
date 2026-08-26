package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.OutboxFile
import dev.fardavide.oltre.client.net.data.SessionStore
import dev.fardavide.oltre.client.save.data.SaveFile
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SessionResponse
import kotlinx.serialization.SerializationException

// **The five lines `OutboxFile`'s own comment promised**, and the whole reason `:client:net:data`
// declares a port instead of reaching for `:client:save:data`'s. Two modules at the same layer that
// both need to write bytes would be a cross-feature edge; the composition root is the one module
// allowed to see both, so the adapter lives here and the warning stays quiet for the right reason.
internal class SaveFileOutbox(private val file: SaveFile) : OutboxFile {

    override suspend fun read(): String? = file.read()

    override suspend fun write(text: String) = file.write(text)

    override suspend fun clear() = file.clear()
}

// The same adapter for the session, and the one place the pair is turned into JSON and back.
//
// **`Protocol.json` and not a codec of its own**, which matters more than it looks: `SessionResponse`
// is the *wire* shape, and decoding a file with a different configuration is how a build ends up
// reading its own credential differently from the way it received it. It also means no
// `ignoreUnknownKeys` — so a session written by a newer build is unreadable rather than misread, and
// unreadable is a sign-in screen the player can answer.
internal class SaveFileSessionStore(private val file: SaveFile) : SessionStore {

    override suspend fun read(): SessionResponse? {
        val text = file.read() ?: return null
        return try {
            Protocol.json.decodeFromString<SessionResponse>(text)
        } catch (_: SerializationException) {
            // `SessionStore.read`'s own contract: nobody signed in and a session that cannot be
            // parsed are one answer, because there is one thing to do about them.
            null
        }
    }

    override suspend fun write(session: SessionResponse) {
        file.write(Protocol.json.encodeToString(session))
    }

    override suspend fun clear() = file.clear()
}

// **Where the colony actually is**, and the one string in the app that names it. `api.oltre.space` is
// the permanent API hostname — the certificate is provisioned, the Apple Return URL is registered
// against it, and the `run.app` URL still answers but is not what ships.
//
// A constant rather than a build-time property for `runningVersion`'s reason one file along: there is
// no generated `BuildConfig` in this build, and one string does not earn source generation. A test
// hands `App` a `FakeOltreApi` and never reaches this.
internal const val OLTRE_BASE_URL = "https://api.oltre.space"
