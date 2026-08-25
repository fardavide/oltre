// **`advanceTimeBy` and `runCurrent` are the point of one of these tests rather than a convenience**
// — the only way to say that a dead server was asked three times *with the waits taken between
// them*, as against three times as fast as a loop can run. They are still marked experimental in
// kotlinx-coroutines 1.11 and have been stable in practice for years; the opt-in is here rather
// than on the module so that it covers exactly this file.
@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.VerbRefusal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-08-25T09:00:00Z")

private val PLAYER = PlayerHandle("davide")

private val UPGRADE = ClientVerb.StartUpgrade(BuildingType.METAL_MINE)

private val SURVEY = ClientVerb.StartSurvey(SystemAddress(galaxy = 2, system = 118))

// A clock a test moves by hand, which is what lets `clientInstant` be asserted at all. `core` reads
// no clock; this is the edge that does, so it is a parameter here for the reason every seam in this
// repository is one.
private class FakeClock(var now: Instant) : Clock {

    override fun now(): Instant = now
}

// Everything the sync needs, built from fakes, so a test says what happened rather than how it was
// wired. The file is held separately because half of these tests are about a second `ColonySync`
// over the same one — which is what an app killed and reopened looks like from in here.
private class Scenario(
    val file: FakeOutboxFile = FakeOutboxFile(),
    val api: FakeOltreApi = FakeOltreApi(colony = fakeColony(NOW)),
    val clock: FakeClock = FakeClock(NOW),
    val keys: FakeIdempotencyKeys = FakeIdempotencyKeys("key"),
    val retry: RetryPolicy = RetryPolicy.ONCE,
) {

    val sync: ColonySync = ColonySync(
        api = api,
        outbox = Outbox(file),
        keys = keys,
        clock = clock,
        retry = retry,
    )

    // What the app becomes after it is killed and reopened: the same file, the same server, a new
    // everything else.
    fun reopened(): ColonySync = ColonySync(
        api = api,
        outbox = Outbox(file),
        keys = FakeIdempotencyKeys("second run"),
        clock = clock,
        retry = retry,
    )
}

class ColonySyncTest {

    @Test
    fun `a tap that reaches the server comes back with the authoritative colony`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW + 2.seconds, seed = 99)))

        // when
        val outcome = scenario.sync.act(PLAYER, UPGRADE)

        // then
        assertEquals(ActOutcome.Synced(fakeColony(NOW + 2.seconds, seed = 99), emptyList()), outcome)
        assertEquals(listOf(UPGRADE), scenario.api.lastSync()?.envelopes?.map { it.verb })
        assertEquals(PLAYER, scenario.api.lastSync()?.player)
    }

    // The colony came back, so the queue is empty — and the file is gone rather than holding `[]`.
    @Test
    fun `a tap the server accepted leaves nothing outstanding`() = runTest {
        // given
        val scenario = Scenario()

        // when
        scenario.sync.act(PLAYER, UPGRADE)

        // then
        assertNull(scenario.file.content)
    }

    // **The property the whole outbox exists for.** `#106` §3: a colony-local verb is queued and
    // validated later, and *"a queued verb that evaporates when the app is killed is worse than one
    // that was refused"*.
    @Test
    fun `a verb tapped with no signal is queued and survives the app being killed`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW), offline = true))

        // when — tapped on a train, and the process it was tapped in goes away
        val outcome = scenario.sync.act(PLAYER, UPGRADE)

        // then
        assertEquals(ActOutcome.Queued, outcome)

        // and when — the app opens again with signal
        scenario.api.offline = false
        val reopened = scenario.reopened().sync(PLAYER)

        // then — the tap goes up, and it is the one that was made rather than a fresh one
        assertIs<SyncOutcome.Synced>(reopened)
        assertEquals(listOf(UPGRADE), scenario.api.lastSync()?.envelopes?.map { it.verb })
        assertNull(scenario.file.content)
    }

    // **The reason an idempotency key is not optional.** The response is lost on a flaky train
    // connection *after* the server did the work; the retry has to carry the key the first attempt
    // carried, or `buildShips` takes the money twice.
    @Test
    fun `a retry after a lost response carries the key its first attempt carried`() = runTest {
        // given — a server that will apply the verb and then fail to say so
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW), losesNextResponse = true))

        // when
        assertEquals(ActOutcome.Queued, scenario.sync.act(PLAYER, UPGRADE))
        val second = scenario.sync.sync(PLAYER)

        // then — the same envelope went up twice under one key
        val sent = scenario.api.syncs().map { it.envelopes.single().idempotencyKey }
        assertEquals(2, sent.size)
        assertEquals(sent.first(), sent.last())

        // and — the server reported it applied rather than applying it again, so the queue drains
        assertIs<SyncOutcome.Synced>(second)
        assertEquals(emptyList(), second.rejected)
        assertNull(scenario.file.content)
        assertEquals(1, scenario.keys.mintCount())
    }

    // `#106` §3's second class, and the dead-control rule reaching the outbox: a galaxy-touching
    // verb tapped with no signal has to be refused **and say so**, never silently dropped. The
    // reason is the answer's own identity — `#113` turns `NotQueueable` into a sentence in the
    // product's own idiom, which a data module cannot build.
    @Test
    fun `a survey tapped with no signal is refused rather than kept`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW), offline = true))

        // when
        val outcome = scenario.sync.act(PLAYER, SURVEY)

        // then
        assertEquals(ActOutcome.NotQueueable, outcome)
        assertNull(scenario.file.content)
        assertEquals(0, scenario.file.writeCount)
    }

    // The other half of look-don't-act, and the half that makes it a rule rather than a refusal:
    // with signal the dispatch goes straight up.
    @Test
    fun `a survey tapped with signal goes up live`() = runTest {
        // given
        val scenario = Scenario()

        // when
        val outcome = scenario.sync.act(PLAYER, SURVEY)

        // then
        assertIs<ActOutcome.Synced>(outcome)
        assertEquals(listOf(SURVEY), scenario.api.lastSync()?.envelopes?.map { it.verb })
    }

    // A queued verb rides along with the live one, in the order it was tapped.
    @Test
    fun `a live dispatch carries whatever was already queued in front of it`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW), offline = true))
        scenario.sync.act(PLAYER, UPGRADE)

        // when
        scenario.api.offline = false
        scenario.sync.act(PLAYER, SURVEY)

        // then
        assertEquals(listOf(UPGRADE, SURVEY), scenario.api.lastSync()?.envelopes?.map { it.verb })
    }

    // **A refusal is data, not an exception** — the sync succeeded, the colony came back, and one
    // of the things the player queued did not survive the replay. It has to be handed up whole
    // (*"no swallowed errors"*) and it has to leave the queue, because replaying it would get the
    // same answer forever.
    @Test
    fun `a rejected verb comes back as data and is reconciled out of the queue`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW), offline = true))
        scenario.api.refusals[UPGRADE] = RejectionReason.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)
        scenario.sync.act(PLAYER, UPGRADE)
        scenario.sync.act(PLAYER, ClientVerb.StartResearch(Technology.PHOTOVOLTAICS))

        // when
        scenario.api.offline = false
        val outcome = scenario.sync.sync(PLAYER)

        // then — the refusal is named, with the envelope it came from
        assertIs<SyncOutcome.Synced>(outcome)
        assertEquals(listOf(UPGRADE), outcome.rejected.map { it.envelope.verb })
        assertEquals(
            listOf(RejectionReason.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)),
            outcome.rejected.map { it.reason },
        )

        // and — judged is judged, whichever way it went
        assertNull(scenario.file.content)
    }

    @Test
    fun `a colony that came back is the one the caller is handed`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW + 1.seconds, seed = 314)))

        // when
        val outcome = scenario.sync.sync(PLAYER)

        // then
        assertEquals(SyncOutcome.Synced(fakeColony(NOW + 1.seconds, seed = 314), emptyList()), outcome)
    }

    // **`ApiError.StaleColony` is answered by syncing again and never by saying anything.** Nothing
    // the player queued was judged — the server lost its own compare-and-set and wrote nothing — so
    // the verbs are still in the outbox and the colony on screen is still the one they last saw.
    @Test
    fun `a stale colony is answered by asking again rather than by telling the player anything`() = runTest {
        // given
        val scenario = Scenario(retry = RetryPolicy.exponential(3, 1.seconds, 3, 30.seconds))
        scenario.api.transientErrors += ApiError.StaleColony

        // when
        val outcome = scenario.sync.sync(PLAYER)

        // then — asked twice, and what came back is a colony rather than an error
        assertIs<SyncOutcome.Synced>(outcome)
        assertEquals(2, scenario.api.syncs().size)
    }

    // And when it never stops losing, the answer is still not a sentence. `NotNow` is what
    // *"nothing to say and nothing to undo"* looks like in the type system.
    @Test
    fun `a colony that stays contended is still nothing to tell the player`() = runTest {
        // given
        val scenario = Scenario(retry = RetryPolicy.exponential(3, 1.seconds, 3, 30.seconds))
        repeat(3) { scenario.api.transientErrors += ApiError.StaleColony }

        // when / then
        assertEquals(SyncOutcome.NotNow, scenario.sync.sync(PLAYER))
        assertEquals(3, scenario.api.syncs().size)
    }

    // Everything else in the taxonomy is terminal by construction: a second `Unauthenticated` is
    // still `Unauthenticated`, and three attempts at it would hold the sign-in screen back four
    // seconds for nothing.
    @Test
    fun `sign in again is not asked twice`() = runTest {
        // given
        val scenario = Scenario(retry = RetryPolicy.exponential(3, 1.seconds, 3, 30.seconds))
        scenario.api.error = ApiError.Unauthenticated

        // when / then
        assertEquals(SyncOutcome.Failed(ApiError.Unauthenticated), scenario.sync.sync(PLAYER))
        assertEquals(1, scenario.api.syncs().size)
    }

    // **A tap that meets a closed door is not a queued tap**, and the difference is what `#113`
    // draws: `Queued` is a row that will land, `Failed` is a sign-in screen. Saying "queued" here
    // would be the dead-control rule in its politest form — a row that looks like it is on its way
    // and is waiting on something the player has not been asked for.
    @Test
    fun `a tap that meets a closed door is a failure rather than a queue`() = runTest {
        // given
        val scenario = Scenario()
        scenario.api.error = ApiError.Unauthenticated

        // when
        val outcome = scenario.sync.act(PLAYER, UPGRADE)

        // then
        assertEquals(ActOutcome.Failed(ApiError.Unauthenticated), outcome)

        // and — the verb is still queued, because nothing about it was judged
        assertEquals(listOf(UPGRADE), Outbox(scenario.file).queued().map { it.verb })
    }

    // A verb queued before the session expired is still queued after it. The failure was about the
    // request and not about the verb, so nothing was judged and nothing is dropped.
    @Test
    fun `a verb queued before a session expired is still queued after it`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = fakeColony(NOW), offline = true))
        scenario.sync.act(PLAYER, UPGRADE)

        // when
        scenario.api.offline = false
        scenario.api.error = ApiError.SessionExpired

        // then
        assertEquals(SyncOutcome.Failed(ApiError.SessionExpired), scenario.sync.sync(PLAYER))
        assertEquals(listOf(UPGRADE), Outbox(scenario.file).queued().map { it.verb })
    }

    // **The whole of "does not hammer a dead server", and it is asserted step by step rather than
    // by a total.** A loop with no backoff would make all three calls before the first millisecond
    // is up, and an elapsed-time assertion alone cannot tell that from three calls spread properly.
    @Test
    fun `a dead server is asked three times with the waits actually taken between them`() = runTest {
        // given
        val scenario = Scenario(
            api = FakeOltreApi(colony = fakeColony(NOW), offline = true),
            retry = RetryPolicy.exponential(attempts = 3, first = 1.seconds, factor = 3, cap = 30.seconds),
        )
        var outcome: SyncOutcome? = null

        // when — the sync is started and time is moved by hand
        val running = launch { outcome = scenario.sync.sync(PLAYER) }
        runCurrent()

        // then — asked once immediately, and not again until the first wait is over
        assertEquals(1, scenario.api.syncs().size)
        advanceTimeBy(999.milliseconds)
        runCurrent()
        assertEquals(1, scenario.api.syncs().size)
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(2, scenario.api.syncs().size)

        // and — the second wait is three times the first
        advanceTimeBy(2999.milliseconds)
        runCurrent()
        assertEquals(2, scenario.api.syncs().size)
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(3, scenario.api.syncs().size)

        // and — three is where it stops, and the queue is intact
        running.join()
        assertEquals(3, scenario.api.syncs().size)
        assertEquals(SyncOutcome.NotNow, outcome)
    }

    @Test
    fun `a tap does not wait around because somebody is looking at the screen`() = runTest {
        // given — the background policy is three attempts, and a tap uses none of it
        val scenario = Scenario(
            api = FakeOltreApi(colony = fakeColony(NOW), offline = true),
            retry = RetryPolicy.exponential(attempts = 3, first = 1.seconds, factor = 3, cap = 30.seconds),
        )

        // when — `runTest` skips a `delay` rather than sleeping through it, so the scheduler is the
        // only thing that can say something did *not* wait
        val before = testScheduler.currentTime
        assertEquals(ActOutcome.Queued, scenario.sync.act(PLAYER, UPGRADE))

        // then
        assertEquals(1, scenario.api.syncs().size)
        assertEquals(before, testScheduler.currentTime)
    }

    @Test
    fun `the instant a verb claims is the instant it was tapped`() = runTest {
        // given
        val scenario = Scenario(
            api = FakeOltreApi(colony = fakeColony(NOW), offline = true),
            clock = FakeClock(NOW + 7.seconds),
        )

        // when
        scenario.sync.act(PLAYER, UPGRADE)

        // then — a claim rather than a fact, which the server clamps; what matters here is that it
        // is the tap's own instant and not the sync's.
        assertEquals(NOW + 7.seconds, Outbox(scenario.file).queued().single().clientInstant)
    }

    @Test
    fun `founding asks the colony route and adopts what it mints`() = runTest {
        // given — a player with no colony, and a galaxy waiting to be adopted
        val scenario = Scenario(api = FakeOltreApi(colony = null, founds = fakeColony(NOW, seed = 5)))

        // when
        val outcome = scenario.sync.found(PLAYER)

        // then
        assertEquals(SyncOutcome.Synced(fakeColony(NOW, seed = 5), emptyList()), outcome)
        assertEquals(listOf(PLAYER), scenario.api.foundings())
    }

    // Not a failure: it is what a first launch of the online build meets before the one-time upload,
    // and the difference between an upgrade and a fresh install is only what happens next.
    @Test
    fun `a player with no colony is told so rather than left waiting`() = runTest {
        // given
        val scenario = Scenario(api = FakeOltreApi(colony = null))

        // when / then
        assertEquals(SyncOutcome.Failed(ApiError.NoColony), scenario.sync.sync(PLAYER))
    }

    @Test
    fun `an app that is behind is told the window it has fallen out of`() = runTest {
        // given
        val scenario = Scenario()
        scenario.api.error = ApiError.UnsupportedApiVersion(
            oldestServed = ApiVersion(2),
            current = ApiVersion(3),
        )

        // when
        val outcome = scenario.sync.sync(PLAYER)

        // then — whole rather than flattened, because *"update the app"* and *"this server is older
        // than you are"* are two different sentences.
        assertIs<SyncOutcome.Failed>(outcome)
        assertEquals(ApiError.UnsupportedApiVersion(ApiVersion(2), ApiVersion(3)), outcome.error)
    }

    @Test
    fun `opening the app with nothing queued sends an empty list rather than skipping the sync`() = runTest {
        // given
        val scenario = Scenario()

        // when
        scenario.sync.sync(PLAYER)

        // then — the empty request is what brings the colony up to date, so it is the normal case
        assertTrue(scenario.api.lastSync()?.envelopes?.isEmpty() == true)
    }
}
