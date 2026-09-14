package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.VerbRefusal
import dev.fardavide.oltre.protocol.VerbRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ReplayTest {

    private val wealthy = establishedColony()

    // The caller's seat, as `replay` is told it: an id and nothing else. It reads no store, so this
    // is the whole of what it knows about an alliance.
    private val FERRO = AllianceId("ferro-alto")

    @Test
    fun `an empty request brings the colony up to date and nothing else`() {
        val replayed = replay(wealthy, envelopes = emptyList(), alreadyApplied = emptySet(), serverNow = TEST_NOW + 2.hours)

        assertEquals(TEST_NOW + 2.hours, replayed.snapshot.lastUpdatedAt)
        assertEquals(advance(wealthy.state, from = TEST_NOW, to = TEST_NOW + 2.hours), replayed.snapshot.state)
        assertEquals(emptySet(), replayed.applied)
        assertEquals(emptyList(), replayed.rejected)
    }

    @Test
    fun `a verb whose response was lost is not applied twice`() {
        val key = IdempotencyKey("the-first-mine")
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = key.value)
        val first = replay(wealthy, listOf(queued), alreadyApplied = emptySet(), serverNow = TEST_NOW)

        // The client never saw that response, so it sends the same envelope again — and the server
        // now holds the key from the write that did land.
        val retried = replay(first.snapshot, listOf(queued), alreadyApplied = setOf(key), serverNow = TEST_NOW)

        assertEquals(setOf(key), retried.applied)
        assertEquals(emptyList(), retried.rejected)
        // One job on the facility and one entry in the log — not two of either, which is what
        // `buildShips` taking the money twice would look like on the screen a verb over.
        assertEquals(1, retried.snapshot.state.builds.size)
        assertEquals(1, retried.snapshot.state.eventLog.count { it is Event.BuildStarted })
    }

    @Test
    fun `a verb core refuses comes back as data rather than as a failure`() {
        // Nanite is gated behind Robotics 10 and this colony has Robotics 1, so the money is there
        // and the rule is not.
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.NANITE_FACTORY), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), alreadyApplied = emptySet(), serverNow = TEST_NOW + 1.hours)

        assertEquals(emptySet(), replayed.applied)
        assertEquals(1, replayed.rejected.size)
        assertEquals(queued, replayed.rejected.single().envelope)
        assertEquals(RejectionReason.Refused(VerbRefusal.REQUIREMENTS_NOT_MET), replayed.rejected.single().reason)
        // The sync succeeded and the colony still moved — that is the whole difference between a
        // rejection and an `ApiError`.
        assertEquals(TEST_NOW + 1.hours, replayed.snapshot.lastUpdatedAt)
    }

    @Test
    fun `a claim from before the colony's own instant is pulled up to it`() {
        // The clock on the device said three hours ago; the authoritative colony is already at
        // `TEST_NOW`, and a verb cannot be applied into a past the state has moved through.
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW - 3.hours)

        val replayed = replay(wealthy, listOf(queued), alreadyApplied = emptySet(), serverNow = TEST_NOW + 1.hours)

        assertEquals(TEST_NOW, startedAt(replayed))
    }

    @Test
    fun `a claim from after the server's own instant is pulled back to it`() {
        // The other end of the clamp, and the one a modified client would reach for: acting in the
        // future is a colony that has accrued time nobody waited through.
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW + 9.hours)

        val replayed = replay(wealthy, listOf(queued), alreadyApplied = emptySet(), serverNow = TEST_NOW + 1.hours)

        assertEquals(TEST_NOW + 1.hours, startedAt(replayed))
    }

    @Test
    fun `a claim between the two instants is taken as it stands`() {
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW + 20.minutes)

        val replayed = replay(wealthy, listOf(queued), alreadyApplied = emptySet(), serverNow = TEST_NOW + 1.hours)

        assertEquals(TEST_NOW + 20.minutes, startedAt(replayed))
    }

    @Test
    fun `envelopes are replayed in the order the player tapped them`() {
        // One applied project at a time, empire-wide — so of two projects queued in one sitting the
        // first one tapped is the one that runs, and the second is refused rather than queued.
        val photovoltaics = envelope(ClientVerb.StartResearch(Technology.PHOTOVOLTAICS), at = TEST_NOW)
        val extraction = envelope(ClientVerb.StartResearch(Technology.EXTRACTION), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(photovoltaics, extraction), emptySet(), serverNow = TEST_NOW)

        assertEquals(setOf(photovoltaics.idempotencyKey), replayed.applied)
        assertEquals(listOf(extraction), replayed.rejected.map { it.envelope })
        assertEquals(RejectionReason.Refused(VerbRefusal.SLOT_BUSY), replayed.rejected.single().reason)
        assertEquals(Technology.PHOTOVOLTAICS, replayed.snapshot.state.activeResearch?.technology)
    }

    @Test
    fun `the same two envelopes in the other order settle the other way`() {
        val photovoltaics = envelope(ClientVerb.StartResearch(Technology.PHOTOVOLTAICS), at = TEST_NOW)
        val extraction = envelope(ClientVerb.StartResearch(Technology.EXTRACTION), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(extraction, photovoltaics), emptySet(), serverNow = TEST_NOW)

        assertEquals(setOf(extraction.idempotencyKey), replayed.applied)
        assertEquals(listOf(photovoltaics), replayed.rejected.map { it.envelope })
        assertEquals(Technology.EXTRACTION, replayed.snapshot.state.activeResearch?.technology)
    }

    @Test
    fun `a galaxy-touching verb tapped a moment ago is put to core like any other`() {
        val queued = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), emptySet(), serverNow = TEST_NOW)

        // `core`'s own answer, which is the proof it got that far — a look-don't-act verb is not
        // refused for being one, only for arriving stale.
        assertEquals(RejectionReason.Refused(VerbRefusal.NO_IDLE_SCOUT), replayed.rejected.single().reason)
    }

    @Test
    fun `a galaxy-touching verb that waited out an offline window is refused unheard`() {
        val queued = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), emptySet(), serverNow = TEST_NOW + 6.hours)

        assertEquals(RejectionReason.NotQueueable, replayed.rejected.single().reason)
        assertEquals(emptySet(), replayed.applied)
    }

    @Test
    fun `a colony-local verb that waited out the same window is replayed`() {
        // The other half of the split, and the reason it is read off `ClientVerb.offlineRule`
        // rather than re-derived: staleness refuses a dispatch and has nothing to say about a mine.
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), emptySet(), serverNow = TEST_NOW + 6.hours)

        assertEquals(setOf(queued.idempotencyKey), replayed.applied)
        assertEquals(TEST_NOW, startedAt(replayed))
    }

    @Test
    fun `a galaxy-touching verb at the edge of the freshness window is still heard`() {
        val queued = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), emptySet(), serverNow = TEST_NOW + FRESH_WINDOW)

        assertEquals(RejectionReason.Refused(VerbRefusal.NO_IDLE_SCOUT), replayed.rejected.single().reason)
    }

    @Test
    fun `a galaxy-touching verb one beat past the window is not`() {
        val queued = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), emptySet(), serverNow = TEST_NOW + FRESH_WINDOW + 1.milliseconds)

        assertEquals(RejectionReason.NotQueueable, replayed.rejected.single().reason)
    }

    @Test
    fun `a stale galaxy-touching verb is answered fresh when the colony is at the same instant`() {
        // The clamp is what decides, not the claim: a colony synced a moment ago is a colony that
        // has not moved, so a verb pulled up to its instant is not being applied to a stale world.
        val colony = wealthy.copy(lastUpdatedAt = TEST_NOW + 6.hours)
        val queued = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW)

        val replayed = replay(colony, listOf(queued), emptySet(), serverNow = TEST_NOW + 6.hours)

        assertEquals(RejectionReason.Refused(VerbRefusal.NO_IDLE_SCOUT), replayed.rejected.single().reason)
    }

    @Test
    fun `two envelopes minted with one key are answered once`() {
        // `SyncResponse` refuses to be built with a key rejected twice, so a client that repeats one
        // in a single request would otherwise take the sync down rather than get an answer.
        val queued = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW, key = "one")

        val replayed = replay(wealthy, listOf(queued, queued), emptySet(), serverNow = TEST_NOW)

        assertEquals(1, replayed.rejected.size)
    }

    @Test
    fun `a key repeated after it landed is not applied a second time`() {
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "one")

        val replayed = replay(wealthy, listOf(queued, queued), emptySet(), serverNow = TEST_NOW)

        assertEquals(setOf(queued.idempotencyKey), replayed.applied)
        assertEquals(emptyList(), replayed.rejected)
        assertEquals(1, replayed.snapshot.state.eventLog.count { it is Event.BuildStarted })
    }

    @Test
    fun `the composability property holds across a sync boundary`() {
        // `brief.md`'s required property, and the server is now a second place it has to hold: a
        // client that syncs once and a client that syncs twice must hold the identical colony.
        val started = replay(
            wealthy,
            listOf(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW)),
            emptySet(),
            serverNow = TEST_NOW,
        ).snapshot

        val once = replay(started, emptyList(), emptySet(), serverNow = TEST_NOW + 8.hours)
        val twice = replay(
            replay(started, emptyList(), emptySet(), serverNow = TEST_NOW + 3.hours).snapshot,
            emptyList(),
            emptySet(),
            serverNow = TEST_NOW + 8.hours,
        )

        // The span is long enough for the build to land inside it, so this is a boundary the events
        // have to survive rather than an interval of pure accrual.
        assertTrue(once.snapshot.state.eventLog.any { it is Event.BuildCompleted })
        assertEquals(once.snapshot, twice.snapshot)
        assertEquals(advance(started.state, from = TEST_NOW, to = TEST_NOW + 8.hours), once.snapshot.state)
    }

    @Test
    fun `a colony stamped in the future is met where it is rather than refused`() {
        // The debug menu writes a colony at the instant it was skipped to, and a server clock can
        // step backwards on its own. `advance` cannot run backwards, and losing a colony to either
        // would be absurd.
        val skipped = wealthy.copy(lastUpdatedAt = TEST_NOW + 2.hours)

        val replayed = replay(skipped, emptyList(), emptySet(), serverNow = TEST_NOW)

        assertEquals(TEST_NOW + 2.hours, replayed.snapshot.lastUpdatedAt)
        assertEquals(skipped.state, replayed.snapshot.state)
    }

    @Test
    fun `a colony that was debugged stays a colony that was debugged`() {
        val debugged = wealthy.copy(debugUsed = true)

        val replayed = replay(debugged, emptyList(), emptySet(), serverNow = TEST_NOW + 1.hours)

        assertTrue(replayed.snapshot.debugUsed)
    }

    @Test
    fun `a refused verb does not move the instant the next one is measured from`() {
        // A refusal keeps nothing, including the advance it was judged against — so the mine queued
        // afterwards still starts at its own claim rather than at the refused verb's.
        val refused = envelope(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), at = TEST_NOW + 30.minutes)
        val accepted = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW + 10.minutes)

        val replayed = replay(wealthy, listOf(refused, accepted), emptySet(), serverNow = TEST_NOW + 1.hours)

        assertEquals(TEST_NOW + 10.minutes, startedAt(replayed))
    }

    // ── The pool ─────────────────────────────────────────────────────────────────────────────
    //
    // **What the accepted verbs owe the alliance, computed here and nowhere else.** The credit
    // cannot be derived from `applied`, which holds keys that were already spent, so it is summed
    // inside the branch where a verb was actually applied — see `Replayed`.

    @Test
    fun `an accepted contribution is what the pool is owed`() {
        val paid = envelope(ClientVerb.Contribute(Resources.of(metal = 4_210, crystal = 960)), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(paid), emptySet(), TEST_NOW + 1.minutes, alliance = FERRO)

        assertEquals(Resources.of(metal = 4_210, crystal = 960), replayed.contributed)
    }

    @Test
    fun `two contributions in one sync are owed together`() {
        val first = envelope(ClientVerb.Contribute(Resources.of(metal = 100)), at = TEST_NOW, key = "one")
        val second = envelope(ClientVerb.Contribute(Resources.of(metal = 250, deuterium = 5)), at = TEST_NOW, key = "two")

        val replayed = replay(wealthy, listOf(first, second), emptySet(), TEST_NOW + 1.minutes, alliance = FERRO)

        assertEquals(Resources.of(metal = 350, deuterium = 5), replayed.contributed)
    }

    // **The trap the field exists for.** A verb whose response was lost is resent; `replay` reports
    // the key as applied because the colony coming back already contains it — and summing over
    // `applied` would credit the pool a second time for resources that left once.
    @Test
    fun `a retried contribution is owed nothing the second time`() {
        val paid = envelope(ClientVerb.Contribute(Resources.of(metal = 4_210)), at = TEST_NOW, key = "same")

        val replayed = replay(wealthy, listOf(paid), setOf(paid.idempotencyKey), TEST_NOW + 1.minutes, FERRO)

        assertEquals(setOf(paid.idempotencyKey), replayed.applied)
        assertEquals(Resources.of(), replayed.contributed)
    }

    // **The one refusal `core` cannot produce**, minted here because this is the only part of the
    // engine that knows who the caller is — and it is checked *before* the verb is applied, so a
    // colony is never debited for a pool that is not there.
    @Test
    fun `a contribution from somebody in no alliance is refused and debits nothing`() {
        val paid = envelope(ClientVerb.Contribute(Resources.of(metal = 4_210)), at = TEST_NOW)

        // Inside the freshness window, so the refusal it earns is the membership one rather than
        // `NotQueueable` — the two are checked in that order and a test that straddled both would
        // be asserting the wrong one.
        val replayed = replay(wealthy, listOf(paid), emptySet(), TEST_NOW + 1.minutes, alliance = null)

        assertEquals(
            listOf(VerbRejection(paid, RejectionReason.Refused(VerbRefusal.NOT_IN_AN_ALLIANCE))),
            replayed.rejected,
        )
        assertEquals(Resources.of(), replayed.contributed)
        // Nothing in the log, which is the half that says the colony was never debited: a refusal
        // keeps nothing, including the advance it was judged against.
        assertTrue(replayed.snapshot.state.eventLog.none { it is Event.ResourcesContributed })
    }

    @Test
    fun `a sync that contributed nothing owes the pool nothing`() {
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(queued), emptySet(), TEST_NOW + 1.minutes, alliance = FERRO)

        assertEquals(Resources.of(), replayed.contributed)
    }

    // **Look-don't-act applies to a contribution too**, and the window is measured against the
    // clamped instant exactly as it is for a run: a basket queued on a train is refused rather than
    // applied against a membership that may have changed.
    @Test
    fun `a contribution older than the window is refused as unqueueable`() {
        val stale = envelope(ClientVerb.Contribute(Resources.of(metal = 10)), at = TEST_NOW)

        val replayed = replay(wealthy, listOf(stale), emptySet(), TEST_NOW + 1.hours, alliance = FERRO)

        assertEquals(listOf(VerbRejection(stale, RejectionReason.NotQueueable)), replayed.rejected)
        assertEquals(Resources.of(), replayed.contributed)
    }

    // The instant the one queued upgrade was actually applied at, read off the log rather than
    // inferred: the event carries the `at` the verb was handed, which is exactly what the clamp
    // decides.
    private fun startedAt(replayed: Replayed): Instant =
        replayed.snapshot.state.eventLog.filterIsInstance<Event.BuildStarted>().single().at
}
