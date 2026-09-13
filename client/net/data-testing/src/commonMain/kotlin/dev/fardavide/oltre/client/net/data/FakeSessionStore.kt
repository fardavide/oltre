package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.SessionResponse

// The port with the platform taken out of it, exactly as `FakeOutboxFile` is. It holds the object
// rather than a string because `SessionStore` does: what a real one serialises is its own business,
// and a fake that made a test spell out JSON would be testing the codec twice.
class FakeSessionStore(private var held: SessionResponse? = null) : SessionStore {

    var writeCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override suspend fun read(): SessionResponse? = held

    override suspend fun write(session: SessionResponse) {
        held = session
        writeCount++
    }

    override suspend fun clear() {
        held = null
        clearCount++
    }
}
