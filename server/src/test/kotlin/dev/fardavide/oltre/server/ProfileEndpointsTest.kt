package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SetProfileRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// **What the two profile routes decide, with no HTTP anywhere in it** — `EndpointsTest`'s shape for
// the pair that has no colony in it. The line between this suite and `OltreServerIntegrationTest` is
// the one `Endpoints.kt` draws: the rules here, the wiring there.
//
// Driven through `HeaderAuthenticator` for `EndpointsTest`'s reason: what is under test is what the
// endpoint does *once it knows who is asking*, and the header is the cheapest way to say. How a
// bearer token is judged is `SessionsTest`'s.
class ProfileEndpointsTest {

    private val colonies = InMemoryColonyRepository()
    private val players = InMemoryPlayerRepository(colonies, ids = sequentialPlayerIds())
    private val authenticator = HeaderAuthenticator(players)
    private val chosen = PlayerProfile(
        name = CommanderName("Ada di Notte"),
        mark = PlayerMark.Preset(MarkPreset.SEXTANT),
    )

    // ── Admission ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `reading a profile with no credential at all is unauthenticated`() = runTest {
        val answer = read(player = null)

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    @Test
    fun `writing a profile with no credential at all is unauthenticated`() = runTest {
        val answer = write(player = null)

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    // **The write is not admitted before the body is read and that ordering is deliberate**: a
    // request with no credential learns nothing about whether its body was acceptable.
    @Test
    fun `a body that is not JSON at all is malformed`() = runTest {
        val answer = write(body = "not a request")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a body carrying a key this build does not know is malformed`() = runTest {
        val answer = write(body = """{"apiVersion":1,"profile":{"name":null,"mark":null},"colour":"red"}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    // **The guard that stops a modified client writing a name the strip cannot draw.** It is
    // `CommanderName`'s `init` reached through decoding, turned into an answer by `readRequest`'s
    // `IllegalArgumentException` arm — the same path a blank idempotency key takes.
    @Test
    fun `a name longer than the contract allows is malformed`() = runTest {
        val overlong = "a".repeat(CommanderName.MAX_LENGTH + 1)

        val answer = write(body = """{"apiVersion":1,"profile":{"name":"$overlong","mark":null}}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a name that was not trimmed before it was sent is malformed`() = runTest {
        val answer = write(body = """{"apiVersion":1,"profile":{"name":"Ada ","mark":null}}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    // The composer never offers this pair — it does not draw the terminus ladder when the path is
    // `NONE` — so a request carrying it came from something that is not the composer. `Composed`'s
    // `init` is what catches it, through the same `IllegalArgumentException` arm.
    @Test
    fun `a terminus on a mark with no path is malformed`() = runTest {
        val mark = """{"type":"Composed","body":"LIMB","path":"NONE","terminus":"DOT"}"""

        val answer = write(body = """{"apiVersion":1,"profile":{"name":null,"mark":$mark}}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a mark this build has no drawing for is malformed rather than accepted`() = runTest {
        val mark = """{"type":"Preset","preset":"SOMETHING_A_LATER_RELEASE_DREW"}"""

        val answer = write(body = """{"apiVersion":1,"profile":{"name":null,"mark":$mark}}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a version beyond this build comes back with the window it does serve`() = runTest {
        val answer = write(body = requestBody(chosen, version = ApiVersion(ApiVersion.CURRENT.value + 1)))

        assertEquals(HttpStatusCode.UpgradeRequired, answer.status)
        assertEquals(
            ApiError.UnsupportedApiVersion(
                oldestServed = ApiVersion.OLDEST_SERVED,
                current = ApiVersion.CURRENT,
            ),
            answer.error(),
        )
    }

    // ── What it answers ───────────────────────────────────────────────────────────────────────

    // The state every account founded before this slice is in — and the reason the strip needs no
    // change to keep working: two nulls, which it draws as `Strings.playerDefaultName()`.
    @Test
    fun `a player who has chosen nothing reads back two absences`() = runTest {
        val answer = read()

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(PlayerProfile(name = null, mark = null), answer.profile())
    }

    @Test
    fun `the write answers with what the row now holds`() = runTest {
        val answer = write(body = requestBody(chosen))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(chosen, answer.profile())
    }

    @Test
    fun `what was written is what the next read says`() = runTest {
        write(body = requestBody(chosen))

        assertEquals(chosen, read().profile())
    }

    // **Replaces rather than merges**, which is the whole reason `null` can mean *clear it*. Without
    // this the only way out of a name a player regrets would be to delete the account.
    @Test
    fun `writing two absences clears a name that was there`() = runTest {
        write(body = requestBody(chosen))

        write(body = requestBody(PlayerProfile(name = null, mark = null)))

        assertEquals(PlayerProfile(name = null, mark = null), read().profile())
    }

    @Test
    fun `a rename sent twice is the same row rather than a second one`() = runTest {
        // No idempotency key anywhere in this pair, and this is why one is not needed: the write is
        // a replacement, so a retry after a lost response says exactly what the first one said.
        write(body = requestBody(chosen))

        assertEquals(chosen, write(body = requestBody(chosen)).profile())
    }

    @Test
    fun `one player's name is not another player's`() = runTest {
        write(body = requestBody(chosen))

        assertEquals(PlayerProfile(name = null, mark = null), read(player = "somebody-else").profile())
    }

    @Test
    fun `the answer says which contract answered it`() = runTest {
        assertEquals(ApiVersion.CURRENT, assertIs<Answer.Profile>(read()).response.apiVersion)
    }

    // ── When the store is not there ───────────────────────────────────────────────────────────

    // Both routes touch a store that is a network away, and Ktor's own answer to a thrown exception
    // is a bare 500 with no `ApiError` in it — which the client reads as `Unreachable` and retries
    // forever rather than as a server having said something.
    @Test
    fun `a store that cannot be reached is an internal error rather than a thrown exception`() = runTest {
        val unreachable = UnreachablePlayerRepository()

        val answer = readProfile(
            HeaderAuthenticator(unreachable),
            unreachable,
            Credentials(authorization = null, playerHeader = DAVIDE),
        )

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertIs<ApiError.Internal>(answer.error())
    }

    @Test
    fun `a store that fails without saying why still names something`() = runTest {
        val speechless = SpeechlessRepository()

        val answer = writeProfile(
            HeaderAuthenticator(speechless),
            speechless,
            Credentials(authorization = null, playerHeader = DAVIDE),
            requestBody(chosen),
        )

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        // The elvis in `answering` earning its place: a `NullPointerException` carries no message,
        // and `"null"` in a log is the one thing worse than nothing for whoever is reading it.
        assertEquals("NullPointerException", assertIs<ApiError.Internal>(answer.error()).detail)
    }

    // **A player the authenticator admitted and the store no longer has** — a deletion landing
    // between `exists` and the write. Not `NoColony`: there is no account either, and the client's
    // answer to that is the gate.
    @Test
    fun `a player who is deleted mid-request is unauthenticated rather than internal`() = runTest {
        val vanishing = VanishingPlayerRepository(colonies)

        val answer = writeProfile(
            HeaderAuthenticator(vanishing),
            vanishing,
            Credentials(authorization = null, playerHeader = DAVIDE),
            requestBody(chosen),
        )

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    private suspend fun read(player: String? = DAVIDE): Answer =
        readProfile(authenticator, players, credentials(player))

    private suspend fun write(body: String = requestBody(chosen), player: String? = DAVIDE): Answer =
        writeProfile(authenticator, players, credentials(player), body)

    private fun credentials(player: String?): Credentials =
        Credentials(authorization = null, playerHeader = player)

    private fun requestBody(profile: PlayerProfile, version: ApiVersion = ApiVersion.CURRENT): String =
        Protocol.json.encodeToString(SetProfileRequest(apiVersion = version, profile = profile))

    private fun Answer.profile(): PlayerProfile = assertIs<Answer.Profile>(this).response.profile

    private fun Answer.error(): ApiError = assertIs<Answer.Failed>(this).error

    private companion object {

        const val DAVIDE = "davide"
    }
}
