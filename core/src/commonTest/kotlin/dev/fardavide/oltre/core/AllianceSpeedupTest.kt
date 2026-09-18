package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// **What an alliance takes off a wait, and the two places it is applied.**
//
// `core` never learns that an alliance exists: the boon arrives as a percentage the server wrote
// into the snapshot, which is the whole of `alliance-sheet.md` §4.2's timing answer — the effective
// instant *is* the sync, so nothing is backdated and `advance` stays composable.
class AllianceSpeedupTest {

    @Test
    fun `no alliance takes nothing off`() {
        assertEquals(0, AllianceSpeedup.NONE.percent)
        assertEquals(60.minutes, 60.minutes.shortenedBy(AllianceSpeedup.NONE))
    }

    @Test
    fun `a speedup takes its percentage off a duration`() {
        assertEquals(43.minutes, 50.minutes.shortenedBy(AllianceSpeedup(14)))
        assertEquals(35.minutes, 50.minutes.shortenedBy(AllianceSpeedup(30)))
    }

    // **The ceiling is a property of the type rather than of the caller**, so no arithmetic anywhere
    // can produce a boon past what the design settled — 2% a level, floored at 30%, which an alliance
    // reaches at level 15.
    @Test
    fun `a speedup past the ceiling cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { AllianceSpeedup(31) }
        assertFailsWith<IllegalArgumentException> { AllianceSpeedup(-1) }
        assertEquals(30, AllianceSpeedup.MAX_PERCENT)
    }

    // ── Where it lands ───────────────────────────────────────────────────────────────────────

    // **Inside the floor, alongside the Robotics divisor.** `upgradeDuration` already argues the
    // ordering: the floor is applied last to what the player actually waits, so a boon placed ahead
    // of it could cut through the minimum and put instant builds back at depth.
    @Test
    fun `a speedup shortens a build and never cuts through the floor`() {
        val alone = PlaceholderBalance.upgradeDuration(
            building = BuildingType.METAL_MINE,
            toLevel = BuildingLevel(13),
            roboticsFactory = BuildingLevel(0),
            naniteFactory = BuildingLevel(0),
            speedup = AllianceSpeedup.NONE,
        )
        val helped = PlaceholderBalance.upgradeDuration(
            building = BuildingType.METAL_MINE,
            toLevel = BuildingLevel(13),
            roboticsFactory = BuildingLevel(0),
            naniteFactory = BuildingLevel(0),
            speedup = AllianceSpeedup(30),
        )

        assertTrue(helped < alone, "a helped build should be shorter: $helped against $alone")

        // The cheapest build in the game is already at the floor, and the strongest alliance in the
        // game may not take it below one.
        val floored = PlaceholderBalance.upgradeDuration(
            building = BuildingType.METAL_MINE,
            toLevel = BuildingLevel(1),
            roboticsFactory = BuildingLevel(20),
            naniteFactory = BuildingLevel(20),
            speedup = AllianceSpeedup(30),
        )
        assertEquals(2.minutes, floored)
    }

    @Test
    fun `a speedup shortens a research start`() {
        val alone = ResearchBalance.researchDuration(
            technology = Technology.ENRICHMENT,
            toLevel = TechLevel(4),
            roboticsFactory = BuildingLevel(0),
            speedup = AllianceSpeedup.NONE,
        )
        val helped = ResearchBalance.researchDuration(
            technology = Technology.ENRICHMENT,
            toLevel = TechLevel(4),
            roboticsFactory = BuildingLevel(0),
            speedup = AllianceSpeedup(30),
        )

        assertEquals(alone * 70 / 100, helped)
    }

    // **A new colony is in no alliance**, which is what keeps every existing balance figure in this
    // repository true: `GameState.initial` opens at `NONE`, so the sim harness and every golden run
    // measure the same durations they always did.
    @Test
    fun `a colony opens with no alliance behind it`() {
        assertEquals(AllianceSpeedup.NONE, GameState.initial(GalaxySeed(1)).allianceSpeedup)
    }
}
