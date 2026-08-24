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
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.worldAt
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
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
//
// **Since 0.12 nothing is tapped to reach the map** — the tab lands on the fold, so the ceremony
// every block used to open with is gone. What replaces it is a *route*: scrub the fold to a system
// and tap the caption under it, which is the two gestures a player makes and the only way to the
// orbit page. The three blocks under "the route" assert that route itself; every block after them
// walks it to reach the page it is really about, which is what makes this file — the one that drives
// the stateful screen — the only place the route can be asserted at all.
class GalaxyFromStateBehaviourTest {

    // ── The route ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `scrubbing the fold and tapping the caption opens the system the scrub selected`() {
        galaxyScreen(state = testGameState) {
            // **The map opens with home selected**, so scrubbing to home would prove nothing: this
            // block is about the neighbour precisely because the tab was already showing 3:171, and
            // an assertion that passed either way is an assertion about the landing rather than about
            // the scrub.
            scrubTo(home.system - 1)
            assertTheCaptionReads("[${home.galaxy}:${home.system - 1}]")

            openTheSelectedSystem()
            assertTheSystemIsDrawn()
            // The address under the system name, which is where every way of going somewhere ends —
            // and the one reading that would catch a push carrying the *selection at landing* rather
            // than the selection the thumb left behind.
            assertTheHeaderNames("${home.galaxy}:${home.system - 1}")
            // A push and not a swap: the fold is gone rather than behind it. That is the whole
            // difference between this gesture and the scale chip below.
            assertNoGalaxyIsDrawn()
        }
    }

    @Test
    fun `the caption sends its probe to the star the map is selecting and not to home`() {
        // **The map's only write, and the class of defect 0.11.1 shipped.** A tap that filled in two
        // thirds of an address from wherever the screen was parked is exactly how a ledger row came
        // to price the wrong world, and the fold makes that easier to reach rather than harder: the
        // selection moves under a thumb, and home is where it starts. So this is asserted on the
        // stateful screen, where the address is actually assembled, rather than on a frame that was
        // handed one.
        val aimed = mutableListOf<SystemAddress>()

        galaxyScreen(state = testGameState, onDispatchProbe = { aimed += it }) {
            scrubTo(elsewhere)
            assertTheCaptionReads("[${home.galaxy}:$elsewhere]")

            dispatchAProbeFromTheMap()
        }

        assertEquals(listOf(SystemAddress(galaxy = home.galaxy, system = elsewhere)), aimed)
    }

    @Test
    fun `the bar under an uncharted star names its address and still offers a probe`() {
        // **The whole of how the dark reads as an invitation, driven end to end.** Empty black says
        // "nothing here" because nothing happens when you touch it; a grain star answers — it takes
        // the selection, fills the bar, and offers the same button every other star offers. The one
        // thing it says that a charted star does not is what a probe there would buy.
        val far = (home.system + 70).coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY)

        galaxyScreen(state = testGameState.copy(resources = Resources.of(metal = 100_000))) {
            scrubTo(far)

            // The address is the name, because it is the only one there is.
            assertTheCaptionReads("[${home.galaxy}:$far]")
            assertTheCaptionReads("uncharted")
            assertTheCaptionReads("charts")
            // And the control is there rather than withheld.
            assertTheCaptionReads("probe")
        }
    }

    @Test
    fun `opening an uncharted star does not hand over what the fog is withholding`() {
        // **The bypass, and it is the whole tier.** The caption's entire bar is a tap target and the
        // tap opens the orbit page — so a player could scrub to any grain star, tap once, and read
        // the name, the region, the class and the world count that the bar two dp above had just
        // refused to say. Every one of those is charted-tier.
        val far = (home.system + 70).coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY)
        val name = systemNameAt(testGameState.galaxy.seed, home.galaxy, far)

        galaxyScreen(state = testGameState.copy(resources = Resources.of(metal = 100_000))) {
            scrubTo(far)
            openTheSelectedSystem()

            assertNothingReads(name)
        }
    }

    @Test
    fun `the orbit page of an uncharted star still sells the flight that would chart it`() {
        // The other half of the page above: it withholds four facts and it must not withhold the
        // control. This is the route a player actually walks — scrub, tap the bar, buy the probe —
        // and it is the one that would have shipped a dead footer, because the footer's own rule
        // predates the tier.
        val far = (home.system + 70).coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY)
        val aimed = mutableListOf<SystemAddress>()

        galaxyScreen(
            state = testGameState.copy(resources = Resources.of(metal = 100_000)),
            onDispatchProbe = { aimed += it },
        ) {
            scrubTo(far)
            openTheSelectedSystem()

            // The page says what it is allowed to and prices the flight. The address is in the
            // *name* slot rather than the coordinate one, because on this tier it is the only name
            // there is — the coordinate slot carries the distance instead.
            assertReads("[${home.galaxy}:$far]")
            assertReads("systems out")
            // Shouted by `SystemHead`, which uppercases the region and the detail as a rendering
            // decision — the catalogue's own strings are lower case.
            assertReads("UNCHARTED")
            assertReads("CHARTS")
            // And nothing the light has not reached: the star's generated name is on no row here.
            assertNothingReads(systemNameAt(testGameState.galaxy.seed, home.galaxy, far))

            dispatchAProbe()
        }

        assertEquals(listOf(SystemAddress(galaxy = home.galaxy, system = far)), aimed)
    }

    @Test
    fun `a probe landing widens the light and the head says so`() {
        // The loop closing, from one screen: the count line before the flight, the flight, and the
        // count line after it. This is the reading Davide gets back — *the area I unlocked* — as a
        // number rather than as a picture.
        val far = SystemAddress(
            galaxy = home.galaxy,
            system = (home.system + 70).coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY),
        )
        val before = testGameState.copy(resources = Resources.of(metal = 100_000))
        val landed = advance(
            assertIs<StartSurveyResult.Started>(startSurvey(before, far, at = EPOCH)).state,
            from = EPOCH,
            to = EPOCH + 3.days,
        )

        galaxyScreen(state = before) { assertReads("61 OF 250 CHARTED") }
        // 141 unchanged at the near end, 241 + 30 clamped to 250 at the far one: 110 systems.
        galaxyScreen(state = landed) { assertReads("110 OF 250 CHARTED") }
    }

    @Test
    fun `choosing a disc describes that galaxy and entering it draws that galaxy`() {
        // **A disc is chosen and then entered, in two gestures rather than one**, and the split is
        // the design: the caption is the map's one readout at both scales, so tapping a disc has to
        // put something *in* it before the tap that acts on it means anything. Selecting and
        // committing in one gesture would be a grid of four buttons rather than a map with a bar.
        galaxyScreen(state = testGameState) {
            toggleTheScale()

            chooseGalaxy(other)
            // The caption follows the disc without leaving the four of them: still the universe, now
            // describing somewhere you have never been.
            assertTheCaptionReads("Galaxy $other")
            assertTheDiscReads(other, "run")
            assertTheUniverseIsUp()

            openTheSelectedSystem()
            // And *now* it steps down, into the galaxy the caption was describing rather than into a
            // system — because the thing selected one scale up is a galaxy.
            assertTheUniverseIsAway()
            assertTheGalaxyIsDrawn()
        }
    }

    @Test
    fun `the scale chip swaps the universe into the map's own frame and back`() {
        galaxyScreen(state = testGameState) {
            toggleTheScale()
            assertTheUniverseIsUp()
            assertNoGalaxyIsDrawn()
            // **The caption stays**, which is what "the map's own frame" means and the only thing that
            // separates a swap from a second screen: the bar at the foot is the same bar, in the same
            // place, describing the galaxy instead of the star.
            assertTheCaptionReads("Galaxy ${home.galaxy}")

            toggleTheScale()
            assertTheUniverseIsAway()
            assertTheGalaxyIsDrawn()

            // Neither gesture went anywhere. The universe is a state of this surface rather than a
            // level you pass through, so there is nothing to come back from — and an orbit page
            // reached by a chip would put the tab bar's one push behind a control that is not one.
            assertNoSystemIsDrawn()
        }
    }

    @Test
    fun `the region name in the system header goes back out to the fold`() {
        galaxyScreen(state = testGameState) {
            openTheSelectedSystem()
            assertTheSystemIsDrawn()

            // The only accent string in the header, and until 0.12 it opened a region index — a screen
            // that listed the region's systems as rows. The fold draws that region as a band with the
            // rest of the galaxy around it, so the same pixels now answer the same question by going
            // back rather than by going deeper.
            openTheMapFromTheHeader()
            assertTheGalaxyIsDrawn()
            assertNoSystemIsDrawn()
        }
    }

    // ── The page the route arrives at ────────────────────────────────────────────────────────

    @Test
    fun `the home system draws its worlds, its ruler and its probe footer`() {
        galaxyScreen(state = testGameState) {
            openTheSelectedSystem()
            // The astronomy line, which is the one reading on the page that is a fact about the
            // *system* rather than about a world — and on your own doorstep it says so.
            assertTheAstronomyReads("Your own system")
            assertTheSystemIsDrawn()
            // Home was surveyed at genesis, so the footer is a receipt rather than an offer.
            assertTheFooterReads("Surveyed at genesis")
        }
    }

    @Test
    fun `a neighbour that has never been looked at is offered a probe at its real price`() {
        galaxyScreen(state = testGameState) {
            scrubTo(home.system - 1)
            openTheSelectedSystem()
            // The price is `SurveyBalance`'s and the flight is the real distance: an unsurveyed
            // system is the one place the footer is a purchase.
            assertTheFooterReads("${SurveyBalance.COST_METAL}")
        }
    }

    @Test
    fun `the bell rides with the probe verb and goes wherever it goes`() {
        // **The second of the two places a flight is bought**, and the reason the map card has a bell
        // at all: an ask reachable only from the dispatch sheet would make which door a player came
        // through decide whether they hear about the landing.
        //
        // Present exactly where the verb is, which is narrower than it sounds — the unaffordable
        // state keeps the button as a ghost carrying a wait, and a bell there would be booking an
        // alert for a flight that is not going anywhere.
        var asked = 0

        galaxyScreen(state = testGameState, onToggleAnnounce = { asked++ }) {
            scrubTo(home.system - 1)
            openTheSelectedSystem()
            tapTheBell()
        }

        assertEquals(1, asked)

        galaxyScreen(state = testGameState.copy(ships = Ships.NONE, resources = Resources.of(metal = 100_000))) {
            scrubTo(home.system - 1)
            openTheSelectedSystem()
            assertTheFooterHasNoBell()
        }
    }

    @Test
    fun `a colony with no scout is told what it needs rather than offered a probe`() {
        // **The state a colony opens in**, since genesis grants no hull and a probe flies a `SCOUT`.
        // The footer must not draw a verb the model would refuse — that is the whole of what this
        // layer is for — and the note names the *Shipyard* rather than a wait, because unlike every
        // other unaffordable state in the game this one is not answered by standing still.
        val broke = testGameState.copy(ships = Ships.NONE, resources = Resources.of(metal = 100_000))

        galaxyScreen(state = broke) {
            scrubTo(home.system - 1)
            openTheSelectedSystem()
            assertTheFooterReads("needs a scout")
            // The offer is still stated — a refusal has to say what it is refusing — and the metal
            // chip must not redden, because the metal is not what is short.
            assertTheFooterReads("${SurveyBalance.COST_METAL}")
        }
    }

    @Test
    fun `a scout on its way home turns the refusal into a countdown`() {
        // The other half of the same refusal. A hull genuinely coming back is a wait, so a countdown
        // is the honest answer and the Shipyard is not the advice — and a countdown to a hull that
        // is *not* coming would be a lie however well it rendered.
        val elsewhere = SystemAddress(galaxy = home.galaxy, system = home.system + 40)
        val out = assertIs<StartSurveyResult.Started>(
            startSurvey(testGameState.copy(resources = Resources.of(metal = 100_000)), elsewhere, at = EPOCH),
        ).state

        galaxyScreen(state = out) {
            scrubTo(home.system - 1)
            openTheSelectedSystem()
            assertTheFooterDoesNotRead("needs a scout")
            assertTheFooterReads("in ")
        }
    }

    @Test
    fun `a probe in flight counts down in the footer of the system it is aimed at`() {
        val target = neighbour()
        val launched = assertIs<StartSurveyResult.Started>(
            startSurvey(testGameState.copy(resources = Resources.of(metal = 100_000)), target, at = EPOCH),
        ).state

        galaxyScreen(state = launched) {
            scrubTo(target.system)
            openTheSelectedSystem()
            // Three parts, exactly as a running build draws them, and no cancel — nothing in this
            // game cancels.
            assertTheFooterReads("lands")
        }
    }

    @Test
    fun `tapping a world raises the sheet the mapper priced for it`() {
        val state = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 2))

        galaxyScreen(state = state) {
            openTheSelectedSystem()
            tapTheWorld(RUNNABLE)
            assertTheSheetIsUp()
            // The coordinate the row printed, because the sheet is a second reading of a world the
            // player has already chosen rather than a second way of choosing one.
            assertTheSheetReads("[${home.galaxy}:${home.system}:${RUNNABLE.slot}]")
            // And the figure the run would actually bring home, which is the only number on the
            // sheet that moves when a control is touched. **In the resource this world is richer in**
            // — read off the seed rather than written down, which is 0.13's correction: this said
            // `"metal"` and passed against the head's `metal 1.15` while the figure said crystal, so
            // it was asserting the wrong half of the sheet and would have gone on doing so.
            assertTheSheetReads(richerAtRunnable)
        }
    }

    @Test
    fun `a fleet bigger than the vein is told what the world can actually give it`() {
        // The clamp, which is where this mechanic lives: eight hulls against a world of this size
        // lift the vein rather than their own capacity, so the figure is the deposit and the note
        // under the stepper names the hulls that would come home empty.
        val bigFleet = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 8))

        galaxyScreen(state = bigFleet) {
            openTheSelectedSystem()
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
            openTheSelectedSystem()
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
            openTheSelectedSystem()
            tapTheWorld(RUNNABLE)
            sendOneMore()
            assertTheSheetIsUp()
            // Two hulls lift twice as much, unless the vein says otherwise — either way the figure
            // under the rule is the one the run would actually be dispatched with.
            assertTheSheetReads(richerAtRunnable)
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
            scrubTo(home.system - 1)
            openTheSelectedSystem()
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
                    // **Charted too, because a survey implies a landing.** Writing `surveyed` by hand
                    // and leaving `charted` alone builds a state the game cannot reach — surveyed and
                    // in the dark at once — and since 0.19 the orbit page would draw none of it.
                ).withCharted(far),
            )
        }

        galaxyScreen(state = surveyed) {
            openTheSelectedSystem()
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
            openTheSelectedSystem()
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
            openTheSelectedSystem()
            tapTheWorld(RUNNABLE)
            bringBack(ResourceKind.CRYSTAL)
            assertTheSheetIsUp()
            assertTheSheetReads("crystal")
        }
    }

    @Test
    fun `the ruler prices another galaxy as a galaxy away rather than as more systems`() {
        galaxyScreen(state = testGameState) {
            openTheSelectedSystem()
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

        // What the sheet defaults to at `RUNNABLE`, and therefore the word its figure is printed in.
        // Read off the generator, because which of the two a world is richer in is the seed's
        // business and a hardcoded answer is this test asserting the map.
        val richerAtRunnable: String = worldAt(testGameState.galaxy.seed, RUNNABLE)!!.traits.let { traits ->
            if (traits.metalRichness.perMillion >= traits.crystalRichness.perMillion) "metal" else "crystal"
        }

        // A galaxy that is not home, so the disc really is somewhere else and its card really is
        // priced. Which one hardly matters — two of the three are one hop away — so it is derived
        // from home rather than written down, and it moves if the seed's home galaxy ever does.
        val other: Int = if (testGameState.galaxy.home.galaxy == 1) 2 else 1

        // A system in another band that nobody has surveyed, so the caption offers a probe rather
        // than quoting a round trip. Two bands down from home and clamped, which keeps it on the map
        // whichever end of the galaxy a seed puts you at.
        val elsewhere: Int = (testGameState.galaxy.home.system + 50).coerceAtMost(250)
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
        val home: GalaxyCoordinate = testGameState.galaxy.home
    }
}
