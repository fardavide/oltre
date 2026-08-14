package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The only thing the galaxy identity slice writes to disk.** Everything else on Claude Design's
// frames — names, epithets, portraits, regions, the ledger's own sort and filters — is derived,
// which is why the slice costs one field rather than a screen's worth of state.
//
// A pin is a coordinate the player said mattered. Its bound is the player's own patience, so
// `WorldDeposit`'s objection to per-world records — *"a counter for every world ever visited is a
// save that grows without bound"* — does not reach it: nothing pins a world except a tap.
//
// The two save-facing halves of this — the round trip and the 11 → 12 hop — live in `GameSaveTest`
// with their siblings, which is where the migration fixtures are.
class GalaxyPinTest {

    @Test
    fun `a fresh colony has pinned nothing`() {
        assertEquals(emptySet(), GameState.initial().galaxy.pinned)
    }

    @Test
    fun `a world cannot be pinned unless it has been surveyed`() {
        // A pin is a bookmark into what you know, and the ledger is the only place one is shown — so
        // a pin on an unsurveyed coordinate would be a row the ledger cannot draw. Refused where the
        // state is built rather than filtered where it is read.
        val fresh = GameState.initial()
        val unseen = GalaxyCoordinate(galaxy = fresh.galaxy.home.galaxy, system = 200, slot = 5)

        val refused = runCatching { fresh.galaxy.copy(pinned = setOf(unseen)) }

        assertTrue(refused.isFailure, "an unsurveyed world was allowed to be pinned")
    }

    @Test
    fun `the home world can be pinned because genesis surveyed it`() {
        // The one coordinate every colony has always been able to pin, and the check that the rule
        // above is a rule about surveying rather than an accidental ban on everything.
        val fresh = GameState.initial()

        val pinned = fresh.galaxy.copy(pinned = setOf(fresh.galaxy.home))

        assertEquals(setOf(fresh.galaxy.home), pinned.pinned)
    }
}
