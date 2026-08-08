package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.time.Duration.Companion.hours
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
        // given a colony one pressure band short of the best world in its home system
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
            assertReads("the same slot")
            assertReads("0.5 … 2.6")

            startTheOnlyProjectOffered()

            // the ladder is the length the sheet says it is, so nothing lands early
            letTimePass(by = 2.hours)
            open(OltreTab.GALAXY)
            assertReads(REMEDY)

            // then, once it completes, the same world reads differently without a survey or a fleet
            letTimePass(by = 2.hours)
            assertReads(HOME_SYSTEM_BEST)
            // The yield rather than the verdict word: two worlds read SETTLEABLE once the empire
            // has climbed this far, and 1.23 is this one's. Slot 10 is still blocked on pressure
            // and still wants Atmospheric 9, so the remedy string leaving the screen is this
            // world's verdict changing rather than the whole system's.
            assertReads("yield 1.23")
            assertNothingReads(REMEDY)
        }
    }

    // The other half of the shared slot, from the other screen: with a ladder running, the applied
    // branch cannot start either, and the Galaxy tab is still readable while it runs.
    @Test
    fun `a running ladder holds the slot against the applied branch`() {
        val game = TestGame(initial = onePressureBandShort(), start = EPOCH)

        game(game) {
            open(OltreTab.RESEARCH)
            startTheOnlyProjectOffered()

            // the countdown is the row's, and no row on either side of the seam offers to start
            assertReads("→ LV 1")
            assertNothingOffersResearch()
        }
    }

    // Seed 20,260,807's home system, which the galaxy suite already reads: slot 13 fails all three
    // bands at genesis and wants Thermal 12, Gravitic 2 and Atmospheric 1. An empire that has
    // climbed the first two is blocked on pressure alone, so exactly one purchase lands it — and
    // the applied levels are deep enough that their next steps are unaffordable, which leaves the
    // colony able to pay for precisely one project. That is the sting the sheet asks for, arranged
    // so the test can name it.
    private fun onePressureBandShort(): GameState {
        val fresh = GameState.initial(GalaxySeed(20_260_807))
        return fresh.copy(
            // Exactly Atmospheric 1's price, and under every other row's.
            resources = Resources.of(metal = 850, crystal = 1_600, deuterium = 250),
            buildings = Buildings.initial().withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(4)),
            research = Research.initial()
                .withLevel(Technology.PHOTOVOLTAICS, TechLevel(5))
                .withLevel(Technology.EXTRACTION, TechLevel(5))
                .withLevel(Technology.ENRICHMENT, TechLevel(4))
                .withLevel(AdaptationTechnology.THERMAL, TechLevel(12))
                .withLevel(AdaptationTechnology.GRAVITIC, TechLevel(4)),
        )
    }

    private companion object {

        val EPOCH = Instant.fromEpochMilliseconds(0)

        // The coldest world in the home system, and the richest thing the seed puts within reach.
        const val HOME_SYSTEM_BEST = "[3:165:13]"

        // Unique on the screen: slot 10 is blocked on pressure too, but wants Atmospheric 9.
        const val REMEDY = "Atmospheric 1"
    }
}
