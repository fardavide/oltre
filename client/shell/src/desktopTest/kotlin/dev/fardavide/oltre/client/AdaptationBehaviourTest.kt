package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.AdaptationBalance
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.adaptationShortlist
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Test

// **The payoff of the whole slice, driven end to end.** The sky reads a world as blocked, Research
// sells the ladder, the project runs, and the same world reads as settleable without a survey or a
// fleet. Until 0.0.17 the sentence on that world ended in a wall; until 0.0.18 Research showed three
// production technologies and no way to buy what the wall had named.
//
// **Since One Sky the Galaxy tab no longer names the level**, and there is no tap from the verdict
// to Research: a world at the world depth reads which axes block it, and the ladder is the Research
// tab's to sell. So the seam this file guards is the verdict — the one sentence both tabs read off
// the same colony, computed by the one `verdictFor`.
//
// Nothing here is stubbed but the clock: `startAdaptation`, `advance` and `verdictFor` are core's.
@OptIn(ExperimentalTestApi::class)
class AdaptationBehaviourTest {

    @Test
    fun `buying the ladder a blocked world names opens that world up`() {
        // given a colony one pressure band short of a world in its home system
        val game = TestGame(initial = onePressureBandShort(), start = EPOCH)

        game(game) {
            // the world is blocked, and the sky says on which axis
            open(OltreTab.GALAXY)
            openTheWorld(TARGET)
            assertTheWorldReads(BLOCKED)
            assertTheWorldReads(ON_PRESSURE)

            // the ladder is sold on Research
            open(OltreTab.RESEARCH)

            assertReads("ADAPTATION")
            assertReads("one ladder at a time")
            // The row's verdict is the same claim the sky made one tab ago, counted rather than
            // described: the level the ladder sells reaches the blocked world, and the row says how
            // many surveyed worlds it reaches in all. **Counted by core and worded by the one
            // formatter**, since which worlds share the target's band is the seed's business and a
            // hand-typed "1 world, 1 worth taking" was the number the nine-galaxy universe moved.
            assertReads("Atmospheric")
            assertReads(ATMOSPHERIC_UNLOCKS)

            startTheAtmosphericLadder()

            // the ladder still takes real time, so nothing lands early. A low Atmospheric level is
            // a few hours at this colony's Robotics 4 — the sheet's 240 a level, carrying the
            // opening discount that went to a tenth at 0.2.7 — so ten minutes in it is still
            // running and the world still reads as blocked.
            letTimePass(by = 10.minutes)
            open(OltreTab.GALAXY)
            openTheWorld(TARGET)
            assertTheWorldReads(BLOCKED)

            // then, once it completes, the same world reads differently without a survey or a
            // fleet — and without leaving it: the sky is still open on that world, and the verdict
            // under the bar is recomputed off the colony the ladder just changed
            letTimePass(by = 7.hours)
            assertTheWorldReads(SETTLEABLE)
            assertNothingReads(BLOCKED)
        }
    }

    // **The split, end to end, and the test this replaces was passing for the wrong reason.** It read
    // "a running ladder holds the slot against the applied branch" and asserted that nothing offered
    // Research once the ladder was running — which was true at 0.11 because of the slot, and stayed
    // true after 0.12.2 removed the slot, because `onePressureBandShort` funds *precisely one
    // project* and the ladder spends it. A green assertion about a rule that no longer exists.
    //
    // Funded properly, the two branches come apart: the ladder runs, an applied technology is still
    // offered, and starting it leaves both in flight at once. Nothing here is stubbed but the clock —
    // `startAdaptation`, `startResearch` and `advance` are core's, on one real colony.
    @Test
    fun `a running ladder leaves the applied branch free to start`() {
        val game = TestGame(initial = onePressureBandShort().richEnoughForAnything(), start = EPOCH)

        game(game) {
            open(OltreTab.RESEARCH)
            startTheAtmosphericLadder()

            // the ladder's own row carries the countdown
            assertReads(ATMOSPHERIC_RUNNING)

            // and the applied branch is untouched by it — which is the whole ruling, and the thing
            // that would have been false one version ago. Enrichment by name rather than "the first
            // one offered": what is being asserted is that a *particular* row the ladder used to
            // block is live, so the row has to be identified rather than discovered.
            startTheEnrichmentProject()

            // both in flight at once: two rows counting down, and the branch that changes the map
            // cost nothing the colony was already doing
            assertReads(ATMOSPHERIC_RUNNING)
            assertReads("→ LV 5")

            // each branch is still one deep, so with both slots full nothing on either side offers
            assertNothingOffersResearch()

            // and both land, on their own clocks, out of one `advance`
            letTimePass(by = 7.hours)
            open(OltreTab.GALAXY)
            openTheWorld(TARGET)
            assertTheWorldReads(SETTLEABLE)
        }
    }

    // **The stock `onePressureBandShort` holds is exactly one ladder level**, which was the point
    // while the slot was shared: it arranged the sting so a test could name it, and "nothing else is
    // offered" meant the slot. With two slots that same arrangement *hides* what is under test —
    // price and rule become indistinguishable again, which is exactly how the test this replaces
    // stayed green through a change that deleted the rule it was named after.
    //
    // Funded past every price on the screen, the assertions can only be about rules. Every remaining
    // row is affordable, so a row that offers nothing is a row its branch's slot is holding.
    private fun GameState.richEnoughForAnything(): GameState = copy(
        resources = Resources.of(metal = 5_000_000, crystal = 5_000_000, deuterium = 5_000_000),
    )

    private companion object {

        val EPOCH = Instant.fromEpochMilliseconds(0)

        // **The first world of the home system, other than home, that pressure blocks at all** —
        // found rather than named, since a slot number is the kind of fact the seed's generation
        // moves under a fixture. The fixture climbs the other two axes for it, so which world it is
        // does not matter; that it wants at least one Atmospheric band does.
        val TARGET: GalaxyCoordinate = run {
            val fresh = GameState.initial(GalaxySeed(20_260_807))
            val home = fresh.galaxy.home
            (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { slot -> GalaxyCoordinate(home.galaxy, home.system, slot) }
                .first { at ->
                    val world = worldAt(fresh.galaxy.seed, at)
                    at != home && world != null &&
                        GalaxyBalance.levelThatTolerates(HostilityAxis.PRESSURE, world.traits.pressure.milliAtm) >= 1
                }
        }

        // The verdict line's first word, and the clause that names the one axis the fixture left
        // short. Neither names a level: since One Sky the sky says *what* blocks, and Research
        // says what buys it.
        const val BLOCKED = "Blocked"
        const val ON_PRESSURE = "pressure"
        const val SETTLEABLE = "Settleable"

        // The pressure band the target sits in, which is the level the fixture leaves the colony one
        // short of and so the level the Research row counts down to.
        val PRESSURE_LEVEL: Int = run {
            val fresh = GameState.initial(GalaxySeed(20_260_807))
            val traits = checkNotNull(worldAt(fresh.galaxy.seed, TARGET)).traits
            GalaxyBalance.levelThatTolerates(HostilityAxis.PRESSURE, traits.pressure.milliAtm)
        }

        val ATMOSPHERIC_RUNNING = "→ LV $PRESSURE_LEVEL"

        // Seed 20,260,807's home system, which the galaxy suite already reads. **The levels and the
        // funding are derived from the target world rather than written out**, because 0.5.1 moved
        // where genesis starts a colony and this fixture had four hand-typed numbers that all had to
        // agree with each other and with a world none of them named — and the nine-galaxy universe
        // moved it again. Derived, the arrangement states itself: climb every axis of `TARGET`
        // except pressure, stop one level short of that, and hold exactly the price of the level
        // that would close it.
        //
        // What the arrangement buys is that **precisely one project is affordable**. The last
        // adaptation level of a ×1.5 ladder is dear enough that the two ladders not being climbed
        // and all three applied technologies are out of reach at the same stock — Gravitic's next
        // step is metal-heavy where Atmospheric's is crystal-heavy, which is the cost table's own
        // design doing the work. That is the sting the sheet asks for, arranged so the test can
        // name it.
        fun onePressureBandShort(): GameState {
            val fresh = GameState.initial(GalaxySeed(20_260_807))
            val traits = checkNotNull(worldAt(fresh.galaxy.seed, TARGET)) { "the target world must exist" }.traits
            val price = AdaptationBalance.adaptationCost(AdaptationTechnology.ATMOSPHERIC, TechLevel(PRESSURE_LEVEL))
            return fresh.copy(
                resources = Resources.of(metal = price.metal, crystal = price.crystal, deuterium = price.deuterium),
                buildings = Buildings.initial().withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(4)),
                research = Research.initial()
                    .withLevel(Technology.PHOTOVOLTAICS, TechLevel(5))
                    .withLevel(Technology.EXTRACTION, TechLevel(5))
                    .withLevel(Technology.ENRICHMENT, TechLevel(4))
                    .withLevel(
                        AdaptationTechnology.THERMAL,
                        TechLevel(GalaxyBalance.levelThatTolerates(HostilityAxis.TEMPERATURE, traits.temperature.celsius)),
                    )
                    .withLevel(
                        AdaptationTechnology.GRAVITIC,
                        TechLevel(GalaxyBalance.levelThatTolerates(HostilityAxis.GRAVITY, traits.gravity.milliG)),
                    )
                    // One short, which is the whole fixture.
                    .withLevel(AdaptationTechnology.ATMOSPHERIC, TechLevel(PRESSURE_LEVEL - 1)),
            )
        }

        // What the row promises, counted by `adaptationShortlist` over the fixture's own colony and
        // worded by the formatter the row uses. The one thing asserted by hand is that the target is
        // among them — otherwise the sentence the row makes and the sentence the sky made would be
        // about different worlds.
        val ATMOSPHERIC_UNLOCKS: String = run {
            val shortlist = adaptationShortlist(onePressureBandShort())
                .single { it.technology == AdaptationTechnology.ATMOSPHERIC }
            check(shortlist.unlocks >= 1) { "the fixture's ladder must reach the target world" }
            English.resolve(Strings.shortlistVerb(unlocks = shortlist.unlocks, worthTaking = shortlist.worthTaking))
        }
    }
}
