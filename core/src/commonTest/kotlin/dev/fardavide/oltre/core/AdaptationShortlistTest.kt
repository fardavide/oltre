package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptationShortlistTest {

    private fun shortlistOf(state: GameState, technology: AdaptationTechnology): LadderShortlist =
        adaptationShortlist(state).single { it.technology == technology }

    @Test
    fun `every ladder is listed, including the ones that would unlock nothing`() {
        // then a row reading zero is what makes the other two mean something
        assertEquals(
            AdaptationTechnology.entries.toList(),
            adaptationShortlist(GameState.initial()).map { it.technology },
        )
    }

    @Test
    fun `each row names the level the player would actually buy`() {
        // given an empire part-way up one ladder
        val state = GameState.initial().let {
            it.copy(research = it.research.withLevel(AdaptationTechnology.GRAVITIC, TechLevel(4)))
        }

        // then
        assertEquals(TechLevel(5), shortlistOf(state, AdaptationTechnology.GRAVITIC).nextLevel)
        assertEquals(TechLevel(1), shortlistOf(state, AdaptationTechnology.THERMAL).nextLevel)
    }

    @Test
    fun `an unsurveyed galaxy shortlists nothing`() {
        // given genesis: only the home system is known
        val state = GameState.initial()

        // then the count is a function of what you have surveyed, which is the whole mechanic —
        // the shortlist is the reason to survey *before* committing the shared research slot
        assertTrue(adaptationShortlist(state).all { it.unlocks <= state.galaxy.surveyed.size })
    }

    @Test
    fun `surveying more can only ever raise a count`() {
        // given
        val base = GameState.initial()
        val neighbour = SystemAddress(
            galaxy = base.galaxy.home.galaxy,
            system = base.galaxy.home.system + 1,
        )
        val wider = base.copy(
            galaxy = base.galaxy.copy(
                surveyed = base.galaxy.surveyed + GalaxyState.occupiedWorldsIn(base.galaxy.seed, neighbour),
            ),
        )

        // then
        for (technology in AdaptationTechnology.entries) {
            assertTrue(
                shortlistOf(wider, technology).unlocks >= shortlistOf(base, technology).unlocks,
                "$technology must not shrink when more of the map is known",
            )
        }
    }

    @Test
    fun `a world blocked on two axes is not unlocked by a level of one of them`() {
        // given a world failing gravity and pressure together
        val base = GameState.initial()
        val at = base.galaxy.home.copy(slot = if (base.galaxy.home.slot == 1) 2 else base.galaxy.home.slot - 1)
        val state = base.copy(galaxy = base.galaxy.copy(surveyed = base.galaxy.surveyed + at))

        // when — measured against the real map rather than a fixture, so the invariant is asserted
        // over whatever the seed produced
        val counts = adaptationShortlist(state)

        // then no row may claim a world that another axis still blocks: the sum of what the three
        // ladders claim cannot exceed the worlds that are blocked at all
        val blocked = state.galaxy.surveyed
            .mapNotNull { worldAt(state.galaxy.seed, it) }
            .count { verdictFor(it, state) is WorldVerdict.Blocked }
        assertTrue(
            counts.sumOf { it.unlocks } <= blocked,
            "the three ladders together cannot unlock more worlds than are blocked",
        )
    }

    @Test
    fun `worth-taking is a subset of what a level unlocks`() {
        // given
        val state = GameState.initial()

        // then the honest half of the pair — most worlds that pass every band are still Barren
        for (row in adaptationShortlist(state)) {
            assertTrue(row.worthTaking <= row.unlocks, "${row.technology} claims more worth taking than unlocked")
        }
    }

    @Test
    fun `climbing a ladder spends its own shortlist`() {
        // given a galaxy wide enough for the count to be non-zero somewhere
        val base = GameState.initial()
        var wide = base
        for (away in 1..12) {
            val system = SystemAddress(galaxy = base.galaxy.home.galaxy, system = base.galaxy.home.system + away)
            wide = wide.copy(
                galaxy = wide.galaxy.copy(
                    surveyed = wide.galaxy.surveyed + GalaxyState.occupiedWorldsIn(wide.galaxy.seed, system),
                ),
            )
        }
        val ladder = adaptationShortlist(wide).maxBy { it.unlocks }
        if (ladder.unlocks == 0) return // nothing to assert on this seed

        // when the player buys exactly the level the shortlist was quoting
        val climbed = wide.copy(
            research = wide.research.withLevel(ladder.technology, ladder.nextLevel),
        )

        // then those worlds really did stop being blocked
        val stillBlocked = wide.galaxy.surveyed
            .mapNotNull { worldAt(wide.galaxy.seed, it) }
            .count { verdictFor(it, climbed) is WorldVerdict.Blocked }
        val wasBlocked = wide.galaxy.surveyed
            .mapNotNull { worldAt(wide.galaxy.seed, it) }
            .count { verdictFor(it, wide) is WorldVerdict.Blocked }
        assertEquals(
            ladder.unlocks,
            wasBlocked - stillBlocked,
            "${ladder.technology} ${ladder.nextLevel} promised ${ladder.unlocks} worlds",
        )
    }
}
