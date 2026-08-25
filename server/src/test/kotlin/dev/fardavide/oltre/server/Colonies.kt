package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.VerbEnvelope
import kotlin.time.Instant

// **A fixed instant, never the wall clock.** The same rule the behaviour suite learned the
// expensive way: a colony founded at `now` mints its galaxy from `now`, so a test that reads the
// clock is a test that plays a different map every run — and a coverage number that moves without a
// diff behind it. Every span in this module's tests is arithmetic on this constant.
internal val TEST_NOW: Instant = Instant.parse("2026-08-25T12:00:00Z")

// A colony as it is the moment it is founded, at a seed that is written down rather than drawn.
internal fun freshColony(at: Instant = TEST_NOW, seed: Long = 20260825): GameSnapshot = GameSnapshot(
    lastUpdatedAt = at,
    state = GameState.initial(GalaxySeed(seed)),
)

// A colony with money in the bank and the one facility the research branch is gated behind. What is
// under test in this module is the replay and the routes; whether a colony can afford a mine is
// `core`'s question and is answered by `core`'s own tests, so a fixture that had to be earned would
// make every test here quietly a balance test as well.
internal fun establishedColony(at: Instant = TEST_NOW, seed: Long = 20260825): GameSnapshot {
    val fresh = freshColony(at, seed)
    return fresh.copy(
        state = fresh.state.copy(
            resources = Resources.of(metal = 500_000, crystal = 500_000, deuterium = 500_000),
            buildings = fresh.state.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
        ),
    )
}

// The envelope of a verb the player claims to have tapped at `at`. The key defaults to something
// unique per verb-and-instant so a test that does not care about idempotency does not have to mint
// one — and a test that does care passes its own.
internal fun envelope(
    verb: ClientVerb,
    at: Instant,
    key: String = "$verb@$at",
): VerbEnvelope = VerbEnvelope(verb = verb, clientInstant = at, idempotencyKey = IdempotencyKey(key))
