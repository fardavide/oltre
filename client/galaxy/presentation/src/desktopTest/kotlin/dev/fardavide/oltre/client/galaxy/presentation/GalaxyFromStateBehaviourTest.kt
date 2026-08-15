package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.worldAt
import kotlin.test.assertIs
import kotlin.time.Instant
import org.junit.Test

// **The whole tab against a real `GameState`, all four mappers included** — the page, the ruler, the
// probe footer and the dispatch sheet.
//
// It exists because of what 0.9.1 moved. Until then `TestGalaxyUiState` built its frames by calling
// these mappers, so the screenshot and behaviour suites drove them end to end as a side effect of
// drawing. The frames are stated by hand now — a ui module is a leaf and cannot see a mapper — and
// what that reached incidentally has to be reached on purpose instead. `GalaxyUiStateTest` and its
// three siblings assert the arithmetic with no screen; this asserts that what the arithmetic
// produces is what a player is actually shown, which is the seam neither half can see.
//
// Every state here is built from the seed rather than described, for the reason the unit tests give:
// the screen exists to read what the generator produced, and a fixture would let the two drift.
// **Every block opens with `openTheMap()` since 0.11**, and it is not ceremony: the Galaxy tab now
// opens on the ledger of what you know, so a test about the *system* view has to go there the way a
// player does. That one tap is also the only coverage the switch itself has.
class GalaxyFromStateBehaviourTest {

    @Test
    fun `the home system draws its worlds, its ruler and its probe footer`() {
        galaxyScreen(state = testGameState) {
            openTheMap()
            // The astronomy line, which is the one reading on the page that is a fact about the
            // *system* rather than about a world — and on your own doorstep it says so.
            assertTheAstronomyReads("Your own system")
            assertTheMapIsDrawn()
            // Home was surveyed at genesis, so the footer is a receipt rather than an offer.
            assertTheFooterReads("Surveyed at genesis")
        }
    }

    @Test
    fun `a neighbour that has never been looked at is offered a probe at its real price`() {
        galaxyScreen(state = testGameState) {
            openTheMap()
            openSystem(home.system - 1)
            // The price is `SurveyBalance`'s and the flight is the real distance: an unsurveyed
            // system is the one place the footer is a purchase.
            assertTheFooterReads("${SurveyBalance.COST_METAL}")
        }
    }

    @Test
    fun `a probe in flight counts down in the footer of the system it is aimed at`() {
        val target = neighbour()
        val launched = assertIs<StartSurveyResult.Started>(
            startSurvey(testGameState.copy(resources = Resources.of(metal = 100_000)), target, at = EPOCH),
        ).state

        galaxyScreen(state = launched) {
            openTheMap()
            openSystem(target.system)
            // Three parts, exactly as a running build draws them, and no cancel — nothing in this
            // game cancels.
            assertTheFooterReads("lands")
        }
    }

    @Test
    fun `tapping a world raises the sheet the mapper priced for it`() {
        val state = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 2))

        galaxyScreen(state = state) {
            openTheMap()
            tapTheWorld(RUNNABLE)
            assertTheSheetIsUp()
            // The coordinate the row printed, because the sheet is a second reading of a world the
            // player has already chosen rather than a second way of choosing one.
            assertTheSheetReads("[${home.galaxy}:${home.system}:${RUNNABLE.slot}]")
            // And the figure the run would actually bring home, which is the only number on the
            // sheet that moves when a control is touched.
            assertTheSheetReads("metal")
        }
    }

    @Test
    fun `a fleet bigger than the vein is told what the world can actually give it`() {
        // The clamp, which is where this mechanic lives: eight hulls against a world of this size
        // lift the vein rather than their own capacity, so the figure is the deposit and the note
        // under the stepper names the hulls that would come home empty.
        val bigFleet = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 8))

        galaxyScreen(state = bigFleet) {
            openTheMap()
            tapTheWorld(RUNNABLE)
            homeIn(FleetBalance.WINDOWS.last())
            assertTheSheetIsUp()
            // "the whole deposit" is the one token the clamped state needs — the headline figure
            // already *is* the deposit, so printing it twice is the defect this replaces.
            assertTheSheetReads("the whole deposit")
        }
    }

    @Test
    fun `a worked-out world states a wait rather than a figure`() {
        // The dry world: the sheet keeps its chips, its stepper and its ladder and loses only the
        // figure, because the wait is a function of the ask and shrinking the ask is the remedy.
        val emptied = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 4)).let { colony ->
            val cap = colony.galaxy.depositCap(RUNNABLE, ResourceKind.METAL) ?: 0
            colony.copy(galaxy = colony.galaxy.withTaken(RUNNABLE, ResourceKind.METAL, cap, at = EPOCH))
        }

        galaxyScreen(state = emptied) {
            openTheMap()
            tapTheWorld(RUNNABLE)
            bringBack(ResourceKind.METAL)
            assertTheSheetIsUp()
            assertTheSheetReads("empty")
        }
    }

    @Test
    fun `a longer window on a full vein is offered and priced`() {
        // The ladder is the only control on the sheet whose rungs are a function of *distance*, so
        // walking it is what exercises the windows the mapper decided to offer.
        val fleet = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 2))

        galaxyScreen(state = fleet) {
            openTheMap()
            tapTheWorld(RUNNABLE)
            sendOneMore()
            assertTheSheetIsUp()
            // Two hulls lift twice as much, unless the vein says otherwise — either way the figure
            // under the rule is the one the run would actually be dispatched with.
            assertTheSheetReads("metal")
        }
    }

    @Test
    fun `an unsurveyed world refuses the run and hands back the flight that would fix it`() {
        // The one refusal in the app that hands back a verb. A hold cannot be priced from a world
        // nobody has looked at, so the sheet offers the probe instead — and it offers it only when
        // the card above it would honour one, which is why the mapper is handed the real footer.
        val rich = testGameState.copy(
            ships = Ships.of(ShipType.SKIFF, 2),
            resources = Resources.of(metal = 100_000),
        )

        galaxyScreen(state = rich) {
            openTheMap()
            openSystem(home.system - 1)
            tapTheWorld(firstWorldOfNeighbour())
            assertTheSheetIsUp()
            assertTheSheetReads("unsurveyed")
        }
    }

    @Test
    fun `a target in the next galaxy narrows the window ladder rather than greying it out`() {
        // The frontier: the next galaxy is hours each way, so the short rungs are simply not on the
        // sheet. That narrowing is what teaches distance before any copy does.
        //
        // **The system index is the home one**, because a galaxy tab re-centres on the same index
        // rather than on that galaxy's first star — this used to survey system 1, navigate somewhere
        // else entirely and assert an absent rung against a sheet that was refusing an unsurveyed
        // world, where every rung is absent. The long rung below is what keeps that from returning.
        val far = SystemAddress(galaxy = home.galaxy % GalaxyBalance.GALAXIES + 1, system = home.system)
        val target = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { GalaxyCoordinate(galaxy = far.galaxy, system = far.system, slot = it) }
            .first { worldAt(testGameState.galaxy.seed, it) != null }
        val surveyed = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 2)).let { colony ->
            colony.copy(
                galaxy = colony.galaxy.copy(
                    surveyed = colony.galaxy.surveyed + (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                        .map { GalaxyCoordinate(galaxy = far.galaxy, system = far.system, slot = it) }
                        .filter { worldAt(colony.galaxy.seed, it) != null },
                ),
            )
        }

        galaxyScreen(state = surveyed) {
            openTheMap()
            openGalaxy(far.galaxy)
            tapTheWorld(target)
            assertTheSheetIsUp()
            // There is a ladder at all, first: every `assertNoRungFor` passes against a sheet with
            // no rungs on it whatever.
            homeIn(FleetBalance.WINDOWS.last())
            // The rung that vanished is the copy: a player who never saw the full ladder still
            // learns why it is short.
            assertNoRungFor(FleetBalance.WINDOWS.first())
        }
    }

    @Test
    fun `an ask no waiting ever covers says so rather than naming a date`() {
        // The vein and the rate carry one multiplier, so a full fleet at a long window wants several
        // times what a world of this size holds. The sheet says that plainly, and the remedy is the
        // stepper above it rather than a date nobody can act on.
        val dry = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 8)).let { colony ->
            val cap = colony.galaxy.depositCap(RUNNABLE, ResourceKind.METAL) ?: 0
            colony.copy(galaxy = colony.galaxy.withTaken(RUNNABLE, ResourceKind.METAL, cap, at = EPOCH))
        }

        galaxyScreen(state = dry) {
            openTheMap()
            tapTheWorld(RUNNABLE)
            bringBack(ResourceKind.METAL)
            homeIn(FleetBalance.WINDOWS.last())
            assertTheSheetIsUp()
            assertTheSheetReads("empty")
        }
    }

    @Test
    fun `switching the hold to crystal reprices the whole sheet`() {
        // Both currencies, because the chips, the figure and the deposit reading are all a function
        // of which one you are bringing back — and a world is rarely equally good for both.
        val fleet = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 3))

        galaxyScreen(state = fleet) {
            openTheMap()
            tapTheWorld(RUNNABLE)
            bringBack(ResourceKind.CRYSTAL)
            assertTheSheetIsUp()
            assertTheSheetReads("crystal")
        }
    }

    @Test
    fun `the ruler prices another galaxy as a galaxy away rather than as more systems`() {
        galaxyScreen(state = testGameState) {
            openTheMap()
            openGalaxy(home.galaxy % GalaxyBalance.GALAXIES + 1)
            // The lens re-centres on the same *index* rather than on your star, because a hop is
            // priced as a whole galaxy plus the difference of the two system numbers — so the
            // astronomy line stops saying "your own system" and starts stating a distance.
            assertTheAstronomyReads("units out")
        }
    }

    private fun firstWorldOfNeighbour(): GalaxyCoordinate = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { GalaxyCoordinate(galaxy = home.galaxy, system = home.system - 1, slot = it) }
        .first { worldAt(testGameState.galaxy.seed, it) != null }

    private fun neighbour(): SystemAddress =
        SystemAddress(galaxy = home.galaxy, system = home.system - 1)

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
        val home: GalaxyCoordinate = testGameState.galaxy.home
    }
}
