package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
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
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Test

// **The payoff of the whole slice, driven end to end.** A blocked world names a ladder, the ladder
// is a tap away, the tap lands on a tab that sells it, the project runs, and the world opens up.
// Until 0.0.17 the sentence on that row ended in a wall; until 0.0.18 it ended in a tab that showed
// three production technologies and no way to buy what the row had just named.
//
// Nothing here is stubbed but the clock: `startAdaptation`, `advance` and `verdictFor` are core's.
@OptIn(ExperimentalTestApi::class)
class AdaptationBehaviourTest {

    @Test
    fun `buying the ladder a blocked world names opens that world up`() {
        // given a colony one pressure band short of the richest world in its home system
        val game = TestGame(initial = onePressureBandShort(), start = EPOCH)

        game(game) {
            // the world is blocked, and the row says what would land it
            open(OltreTab.GALAXY)
            assertReads(HOME_SYSTEM_BEST)
            assertReads(REMEDY)

            // tapping the remedy is what the accent promises: it goes where the thing is sold
            tapTheRemedy(REMEDY)
            assertShowing(OltreTab.RESEARCH)

            // and the tab that opens is already showing the ladder, with no scrolling to do
            assertReads("ADAPTATION")
            assertReads("one ladder at a time")
            // The band the empire already holds used to be the thing asserted here; since the
            // verdict took that line it is the first sentence of the sheet the row opens, which is
            // a claim about the Research screen and is made there.
            //
            // What this file still has to show is that the tap landed on **this empire's**
            // Atmospheric row rather than on a Research tab that happens to contain the word — and
            // the verdict is a better witness than the band ever was, because it is the same claim
            // the Galaxy row made one tap ago, counted rather than described: the ladder the
            // blocked world named reaches exactly that world, and that world is worth taking.
            assertReads("Atmospheric")
            assertReads("Unlocks 1 world, 1 worth taking")

            startTheAtmosphericLadder()

            // the ladder still takes real time, so nothing lands early. Atmospheric 3 is 6h 22m at
            // this colony's Robotics 4 — the sheet's 240 a level, carrying the opening discount
            // that went to a tenth at 0.2.7 — so ten minutes in it is still running and the world
            // still reads as blocked.
            letTimePass(by = 10.minutes)
            open(OltreTab.GALAXY)
            assertReads(REMEDY)

            // then, once it completes, the same world reads differently without a survey or a fleet
            letTimePass(by = 7.hours)
            assertReads(HOME_SYSTEM_BEST)
            // **The world's own name rather than its yield, since 0.11.** The point of the
            // assertion is unchanged — it has to name *which* world moved, because slot 10 is still
            // blocked on pressure and wants Atmospheric 4, so a system-wide reading would pass on
            // the wrong world. What changed is that a settleable row no longer prints a yield: its
            // one note line is "Nothing here blocks a colony", and the thing that identifies the
            // row is the headline the whole slice exists to give it.
            assertReads("Nothing here blocks a colony.")
            assertNothingReads(REMEDY)
        }
    }

    // **The split, end to end, and the test this replaces was passing for the wrong reason.** It read
    // "a running ladder holds the slot against the applied branch" and asserted that nothing offered
    // Research once the ladder was running — which was true at 0.11 because of the slot, and stayed
    // true after 0.12.1 removed the slot, because `onePressureBandShort` funds *precisely one
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
            assertReads("→ LV 3")

            // and the applied branch is untouched by it — which is the whole ruling, and the thing
            // that would have been false one version ago. Enrichment by name rather than "the first
            // one offered": what is being asserted is that a *particular* row the ladder used to
            // block is live, so the row has to be identified rather than discovered.
            startTheEnrichmentProject()

            // both in flight at once: two rows counting down, and the branch that changes the map
            // cost nothing the colony was already doing
            assertReads("→ LV 3")
            assertReads("→ LV 5")

            // each branch is still one deep, so with both slots full nothing on either side offers
            assertNothingOffersResearch()

            // and both land, on their own clocks, out of one `advance`
            letTimePass(by = 7.hours)
            open(OltreTab.GALAXY)
            assertNothingReads(REMEDY)
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

    // Seed 20,260,807's home system, which the galaxy suite already reads. **The levels and the
    // funding are derived from the target world rather than written out**, because 0.5.1 moved
    // where genesis starts a colony and this fixture had four hand-typed numbers that all had to
    // agree with each other and with a world none of them named. Derived, the arrangement states
    // itself: climb every axis of `TARGET` except pressure, stop one level short of that, and hold
    // exactly the price of the level that would close it.
    //
    // What the arrangement buys is that **precisely one project is affordable**. The last
    // adaptation level of a ×1.5 ladder is dear enough that the two ladders not being climbed and
    // all three applied technologies are out of reach at the same stock — Gravitic's next step is
    // metal-heavy where Atmospheric's is crystal-heavy, which is the cost table's own design doing
    // the work. That is the sting the sheet asks for, arranged so the test can name it.
    private fun onePressureBandShort(): GameState {
        val fresh = GameState.initial(GalaxySeed(20_260_807))
        val traits = checkNotNull(worldAt(fresh.galaxy.seed, TARGET)) { "the target world must exist" }.traits
        val pressureLevel = GalaxyBalance.levelThatTolerates(HostilityAxis.PRESSURE, traits.pressure.milliAtm)
        val price = AdaptationBalance.adaptationCost(AdaptationTechnology.ATMOSPHERIC, TechLevel(pressureLevel))
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
                .withLevel(AdaptationTechnology.ATMOSPHERIC, TechLevel(pressureLevel - 1)),
        )
    }

    private companion object {

        val EPOCH = Instant.fromEpochMilliseconds(0)

        // The hottest world in the home system, and the richest thing the seed puts within reach:
        // it fails all three bands at genesis and wants Thermal 7, Gravitic 2 and Atmospheric 3.
        val TARGET = GalaxyCoordinate(galaxy = 3, system = 171, slot = 1)

        const val HOME_SYSTEM_BEST = "[3:171:1]"

        // Unique on the screen: slot 10 is blocked on pressure too, but wants Atmospheric 4.
        const val REMEDY = "Atmospheric 3"
    }
}
