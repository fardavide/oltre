package dev.fardavide.oltre.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiVersionTest {

    @Test
    fun `the version this build speaks is served`() {
        assertTrue(ApiVersion.CURRENT.isServed())
    }

    @Test
    fun `the oldest build still on a phone is served`() {
        assertTrue(ApiVersion.OLDEST_SERVED.isServed())
    }

    // The case the whole field exists for. A merge publishes to TestFlight and cuts an Android
    // release, and a client update is never atomic with a server deploy — so the interesting
    // direction is a *newer* client meeting an older server, which is what this is.
    @Test
    fun `a version past the one this build speaks is not served`() {
        assertFalse(ApiVersion(ApiVersion.CURRENT.value + 1).isServed())
    }

    // Raising `OLDEST_SERVED` strands every install still speaking the version below it, so the one
    // thing worth checking about the pair is that it is still a window rather than an empty set.
    @Test
    fun `the oldest served version is never newer than the current one`() {
        assertTrue(ApiVersion.OLDEST_SERVED <= ApiVersion.CURRENT)
    }

    // Nonsense is answered rather than refused: the server replies `UnsupportedApiVersion` and the
    // client says something useful, where a constructor that threw would make it a parse failure.
    @Test
    fun `a version that is not a version is simply not served`() {
        assertFalse(ApiVersion(0).isServed())
    }

    @Test
    fun `versions order by their number`() {
        assertTrue(ApiVersion(1) < ApiVersion(2))
        assertEquals(ApiVersion(2), maxOf(ApiVersion(1), ApiVersion(2)))
    }

    @Test
    fun `a version survives a round trip`() {
        val text = Protocol.json.encodeToString(ApiVersion.serializer(), ApiVersion.CURRENT)
        assertEquals(ApiVersion.CURRENT, Protocol.json.decodeFromString(ApiVersion.serializer(), text))
    }

    // It encodes as the bare integer rather than as an object, which is what a value class buys and
    // what a hand-written client on the other end would expect to send.
    @Test
    fun `a version encodes as its number`() {
        assertEquals("1", Protocol.json.encodeToString(ApiVersion.serializer(), ApiVersion(1)))
    }
}
