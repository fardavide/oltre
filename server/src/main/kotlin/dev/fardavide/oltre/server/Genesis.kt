package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import kotlin.time.Instant

// **Founding a colony, and it is the one responsibility this slice takes off the client.**
// `GameSession.resume` minted the galaxy from the device clock at genesis (`GameSession.kt:60`);
// from here the server does, because the server is the composition root now.
//
// In a file of its own rather than beside the route that calls it, and that is the
// `test-coverage` skill's own rule rather than tidiness: a unit test that named something in
// `OltreServer.kt` would load the whole Ktor module with it — the routes, the admission and the
// error mapping, none of which a unit test can reach — exactly as reaching for a constant in a file
// full of composables drags a screen into the report. Genesis is a rule; routing is plumbing; they
// are measured by different kinds of test and so they are different files.
internal fun newColony(player: PlayerId, at: Instant): GameSnapshot = GameSnapshot(
    lastUpdatedAt = at,
    state = GameState.initial(galaxySeedFor(player, at)),
)

// **The seed, minted where the clock is.** `core` cannot draw one — it reads no clock and no random
// source — so somebody with both has to, once, at genesis.
//
// **Derived rather than drawn**, exactly as the client's version was and for the same reason: a pure
// function of its arguments is one a test can pin, and one that a retry cannot quietly change under.
// Founding is idempotent at the repository, so this is never asked twice for one colony anyway; what
// purity buys is that the question has an answer somebody can check.
//
// **The player is folded in and the client's version had no need to.** One device founds one colony;
// a server founds them for everybody, and two people signing up inside the same millisecond would
// otherwise open in the identical galaxy and never know it. The mixing is `GameSave.seedFor`'s, term
// for term — it is a hash rather than a cipher, and it is not asked to be one: a galaxy seed is
// public the moment the map is drawn.
internal fun galaxySeedFor(player: PlayerId, at: Instant): GalaxySeed {
    var hash = at.toEpochMilliseconds()
    for (character in player.value) hash = hash * 31 + character.code
    return GalaxySeed(hash)
}
