package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRobot
import dev.fardavide.oltre.client.galaxy.ui.LedgerFilter
import dev.fardavide.oltre.client.galaxy.ui.PHONE_WIDTH
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.regionOf
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.math.abs
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test

// **The screen the Galaxy tab opens on, driven the way a finger drives it.** Everything here goes
// through `galaxyScreen`, the stateful harness, and never through a handed frame — the mode, the
// query, the chip set, the sort and the discovery boundary are all `GalaxyScreen`'s own `remember`s,
// so a *tap that changes one of them* is a claim only this harness can make. `LedgerUiStateTest`
// already asserts what the mapper computes; what is missing without this file is that touching the
// control reaches the mapper at all.
//
// **No coordinate and no name is written down.** The galaxy is generated, so `Calanova VIII` would be
// an assertion about the seed rather than about the ledger, and it would go quietly vacuous the day
// genesis moves — which it did at 0.5.1. Every fixture below scans the survey set instead, which
// picks the same world on every run and still picks *a* world after a reseed.
@OptIn(ExperimentalTestApi::class)
class LedgerBehaviourTest {

    @Test
    fun `the tab opens on the worlds you already know`() {
        // **The slice's headline decision and nothing else asserted it.** Davide's call, 2026-08-14:
        // the map is where you spend probes and the ledger is where you spend ships, and runs go out
        // several times a day where probes go once or twice — so before this the rarer errand was
        // sitting in the commoner one's chair. Nothing is tapped in this block, and that is the test.
        galaxyScreen(state = wellTravelledState) {
            assertNoMapIsDrawn()
            // The head counts what the body lists, and with no query and no chip that is the whole
            // survey set — the set the slice exists to make valuable.
            assertReads("${wellTravelledState.galaxy.surveyed.size} WORLDS")
            assertTheLedgerLists(nearest)
        }
    }

    @Test
    fun `the switch crosses to the map and back and what was typed survives the trip`() {
        // The map is one tap away and the way back is one tap, which is what bought the default: no
        // tab was added and nothing else on the screen moved. The query surviving the crossing is the
        // other half — the field is **always visible and never a mode**, so coming back is not
        // retyping.
        galaxyScreen(state = wellTravelledState) {
            search(ambiguousName)
            assertReads("1 WORLD")

            openTheMap()
            assertTheMapIsDrawn()

            openTheLedger()
            assertNoMapIsDrawn()
            assertReads("1 WORLD")
        }
    }

    @Test
    fun `the map is still one tap away when the query has emptied the ledger`() {
        // The head sits above all three bodies and is gated by none of them. A query that finds
        // nothing is exactly where a player most wants the other view, so a switch that lived inside
        // the list would have taken the way out away at the one moment it was worth having.
        galaxyScreen(state = wellTravelledState) {
            search(UNSPELLABLE)
            assertReads("No world you know is called that.")

            openTheMap()
            assertTheMapIsDrawn()
        }
    }

    // ── Search, the literal answer to "pagine gialle in the 90s" ─────────────────────────────

    @Test
    fun `typing a system's name leaves that system's worlds and drops every other`() {
        // A world's name is its system's name plus a numeral, so one query answers *show me that
        // place* — the one thing on this tab where typing beats tapping, and the reason the field is
        // never behind a mode.
        galaxyScreen(state = wellTravelledState) {
            assertTheLedgerLists(elsewhere)

            search(homeSystemName)

            assertTheLedgerLists(atHome)
            assertNothingIsListed(elsewhere)
        }
    }

    @Test
    fun `a whole name wins outright over the names that contain it`() {
        // **The rule a substring match alone would break**, and the fixture is chosen to be the case
        // it exists for: the numerals are substrings of one another — `Calanova I` is inside
        // `Calanova II`, `III`, `IV`, `VI`, `VII`, `VIII`, `IX`, `XI`… — so widening on a full name
        // would hand back twelve rows, which is the phone book again with extra steps.
        galaxyScreen(state = wellTravelledState) {
            search(ambiguousName)

            // Never "1 worlds": the count sits under the field and is read as a sentence.
            assertReads("1 WORLD")
            // The row is asked for by its address rather than by the name that was typed, because
            // the field carries the query as `EditableText` — so a query for a name matches the row
            // *and the field it was typed into*, and the ambiguity would be the harness's rather
            // than the ledger's.
            assertTheLedgerLists(ambiguousAddress)
        }
    }

    @Test
    fun `a name no palette can spell is answered with a sentence rather than an empty list`() {
        // Every head in the generator is three or four consonant-ended letters and no tail doubles a
        // z, so nothing can be called this. The answer explains the *rule* rather than apologising,
        // because a player who typed half a name needs to know that halves work and whole ones are
        // unambiguous.
        galaxyScreen(state = wellTravelledState) {
            search(UNSPELLABLE)

            assertReads("NO WORLDS")
            assertReads("No world you know is called that.")
            assertReads("Names are unique in a galaxy, so a full name finds one place.")
            assertNothingIsListed(nearest)
        }
    }

    // ── The chips and the sort ───────────────────────────────────────────────────────────────

    @Test
    fun `a chip narrows the ledger and tapping it again gives it back`() {
        // **A chip is a toggle rather than a mode**, which is what lets the tab open with no filter
        // to undo — and untapping has to be a real way back rather than a state the screen keeps.
        //
        // The region chip, because it is the one of the five whose subject moves with the player, and
        // because *both* halves of the claim are assertable with it: a world outside the region
        // leaves and a world inside it stays. Without the second, a chip that wiped the list whole
        // would pass this test.
        galaxyScreen(state = wellTravelledState) {
            assertTheLedgerLists(outsideTheRegion)

            toggle(regionChip)
            assertNothingIsListed(outsideTheRegion)
            assertTheLedgerLists(insideTheRegion)

            toggle(regionChip)
            assertTheLedgerLists(outsideTheRegion)
        }
    }

    @Test
    fun `changing the sort re-orders the rows rather than filtering them`() {
        // The pair the two orders disagree about, so the flip cannot be luck: one world is strictly
        // nearer and the other strictly richer, which puts each at the top of exactly one order.
        //
        // The count is asserted on both sides because a sort that quietly dropped a row would have
        // passed the ordering assertion — **an order moves rows and never removes them.**
        galaxyScreen(state = wellTravelledState) {
            assertReads("NEAREST FIRST")
            assertListedAbove(nearerOfThePair, richerOfThePair)

            changeTheSort()

            assertReads("RICHEST")
            assertListedAbove(richerOfThePair, nearerOfThePair)
            assertReads("${wellTravelledState.galaxy.surveyed.size} WORLDS")
        }
    }

    @Test
    fun `a pinned world is listed above the rest under its own heading and never twice`() {
        // The one thing on this screen that reaches the save. The pin is put on the world with the
        // **longest** round trip, which `nearest first` belongs at the very bottom of the list — so
        // finding it above the nearest row is the pin doing it and not the order.
        //
        // A pin changes *where a thing is*, which is what a pin means; drawn in both sections it
        // would instead be a row the player has to notice is the same row twice.
        galaxyScreen(state = pinnedFarthest) {
            assertReads("PINNED")
            assertListedAbove(farthest, nearest)
            assertListedOnce(farthest)
        }
    }

    // ── The survey moment ────────────────────────────────────────────────────────────────────
    //
    // Two looks rather than one, and that is why these are the only tests here not driven through
    // `galaxyScreen`. **A discovery is the gap between two check-ins**: `seenAt` opens at the instant
    // the tab was first composed and the section holds what landed after it, so a harness that hands
    // the screen one state at one instant can only ever describe a single look and would find the
    // section empty by construction. `galaxyAcrossACheckIn` hands it the second look as well.

    @Test
    fun `a probe that lands while the tab is open arrives as a discovery`() {
        galaxyAcrossACheckIn(opening = awaitingALanding, at = FIXTURE_NOW) { comeBackTo ->
            // Nothing has landed yet. A section that appeared before the probe did would be the
            // screen announcing a survey it has not run.
            assertNothingReads("found ")

            comeBackTo(landed, backAt)

            // One discovery gets the ceremony — the large portrait and the labelled axis column —
            // which is why the fixture flies its probe at a system holding exactly one world.
            assertReads("SURVEYED")
            // **"found 30m ago", not "found day 9"**: nothing in `GameState` carries a genesis
            // instant, so a day number is not derivable where elapsed-since is. Arithmetic rather
            // than a literal, because the probe was really flown and really landed.
            assertReads("found 30m ago")
        }
    }

    @Test
    fun `looking at the ledger banks what it showed so no discovery is met twice`() {
        // **No seen-set is stored and none has to be.** Leaving the ledger is what banks them: they
        // were on screen, so they have been seen, and anything surveyed after that is new again.
        // This is the whole of what makes a discovery card impossible to meet twice.
        galaxyAcrossACheckIn(opening = awaitingALanding, at = FIXTURE_NOW) { comeBackTo ->
            comeBackTo(landed, backAt)
            assertReads("SURVEYED")

            openTheMap()
            openTheLedger()

            assertNothingReads("SURVEYED")
            // ...and the world is still in the ledger. What expired is the announcement, not the
            // reading — asked for by coordinate because that is the row's key and it is unique where
            // a name shared with a discovery card would not be.
            assertTheLedgerLists(foundWorld.at.label())
        }
    }

    // ── Nothing left ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `two chips that cannot both be true name the one to drop`() {
        // A world one Thermal level from tolerable is by definition a world the current level does
        // *not* tolerate, so it is blocked and never settleable: these two chips exclude everything
        // whatever the seed generated, which is what makes this the empty state rather than a fixture
        // that happens to be empty today.
        //
        // **Never a dead end, always the next number** — the `time-until-affordable` pattern applied
        // to a query. It names how many worlds dropping one chip gives back, which is the figure the
        // player is actually deciding against, rather than advice.
        galaxyScreen(state = wellTravelledState) {
            toggle("settleable")
            toggle("one level away")

            assertReads("No world matches all 2.")
            assertReads("match without settleable.")
            assertNothingIsListed(nearest)
        }
    }

    // ── The harness for a second look ────────────────────────────────────────────────────────

    // `galaxyScreen` with the state and the instant hoisted into a `mutableStateOf`, so a test can
    // put the app down and pick it up again. Everything else about it is that harness — same size,
    // same time zone, same frozen clock — and it lives here rather than beside `galaxyScreen` because
    // the discovery section is the only subject in the feature that needs two looks to have one.
    private fun galaxyAcrossACheckIn(
        opening: GameState,
        at: Instant,
        block: GalaxyRobot.(comeBackTo: (GameState, Instant) -> Unit) -> Unit,
    ) {
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 852) {
            val session = mutableStateOf(opening to at)
            setContent {
                OltreTheme {
                    Surface {
                        GalaxyScreen(
                            state = session.value.first,
                            now = session.value.second,
                            timeZone = TimeZone.UTC,
                            onOpenResearch = {},
                            onDispatchProbe = {},
                            onDispatchRun = { _, _, _, _ -> },
                        )
                    }
                }
            }
            GalaxyRobot(this).block { state, now ->
                session.value = state to now
                waitForIdle()
            }
        }
    }

    private companion object {

        // A string no palette can spell — every head is three or four consonant-ended letters and no
        // tail doubles a z.
        const val UNSPELLABLE = "Zzz"

        // Names no other name in the ledger contains, which is what a robot needs and what a query
        // does not: `onNodeWithText` matches on a substring, so asking for `Calanova I` would find
        // twelve rows and fail on the ambiguity rather than on the claim.
        val unmistakable: List<Pair<GalaxyCoordinate, String>> = wellTravelledState.unmistakable()

        val nearest: String = unmistakable.minBy { wellTravelledState.tripTo(it.first) }.second
        val farthest: String = unmistakable.maxBy { wellTravelledState.tripTo(it.first) }.second

        // A world in the home system and one outside it whose name the home system's cannot drag
        // along — the second condition is the query rule from the other side, since widening is a
        // `contains` on the world's whole name.
        val homeSystemName: String = systemNameAt(
            wellTravelledState.galaxy.seed,
            wellTravelledState.galaxy.home.galaxy,
            wellTravelledState.galaxy.home.system,
        )
        val atHome: String = unmistakable
            .first { (at, _) -> at.system == wellTravelledState.galaxy.home.system }
            .second
        val elsewhere: String = unmistakable
            .first { (at, name) ->
                at.system != wellTravelledState.galaxy.home.system &&
                    !name.contains(homeSystemName, ignoreCase = true)
            }
            .second

        // A name at least one other name in the ledger contains — the case the exact-match rule was
        // written for, and therefore the only honest fixture for it.
        val ambiguous: Pair<GalaxyCoordinate, String> = wellTravelledState.galaxy.surveyed
            .map { it to worldNameAt(wellTravelledState.galaxy.seed, it) }
            .let { pairs ->
                pairs.first { (_, candidate) -> pairs.count { candidate in it.second } > 1 }
            }
        val ambiguousName: String = ambiguous.second
        val ambiguousAddress: String = ambiguous.first.label()

        // The region chip as the head actually offers it, and one world on each side of it. It is the
        // one filter whose label and subject move with where the player is standing, so writing the
        // chip by hand here would be building a different chip from the one on the screen.
        val regionChip: String = wellTravelledState
            .availableFiltersFor(wellTravelledState.homeSelection())
            .filterIsInstance<LedgerFilter.Region>()
            .single()
            .let { it.name.substringBefore(' ') }
        val insideTheRegion: String = unmistakable
            .first { (at, _) -> regionOf(at.system) == regionOf(wellTravelledState.galaxy.home.system) }
            .second
        val outsideTheRegion: String = unmistakable
            .first { (at, _) -> regionOf(at.system) != regionOf(wellTravelledState.galaxy.home.system) }
            .second

        // The pair `nearest first` and `richest` disagree about: strictly nearer on one metric and
        // strictly poorer on the other, so each is above the other in exactly one order. Searched for
        // rather than named, because which two worlds those are is the seed's business.
        val disagreeing: Pair<String, String> = wellTravelledState.disagreeingPair()
        val nearerOfThePair: String = disagreeing.first
        val richerOfThePair: String = disagreeing.second

        // The farthest world pinned, and nothing else changed.
        val pinnedFarthest: GameState = wellTravelledState.let { state ->
            val at = unmistakable.maxBy { state.tripTo(it.first) }.first
            state.copy(galaxy = state.galaxy.copy(pinned = setOf(at)))
        }

        // **A real probe, really landed.** Nothing about a discovery is stored on a world — it is a
        // `SurveyCompleted` in the log with an instant on it — so this flies one rather than writing
        // an event by hand, which is also what makes the "found" stamp arithmetic rather than a
        // literal.
        val probeTarget: SystemAddress = wellTravelledState.aSystemHoldingOneUnsurveyedWorld()
        val awaitingALanding: GameState = assertIs<StartSurveyResult.Started>(
            startSurvey(
                wellTravelledState.copy(resources = Resources.of(metal = 100_000)),
                probeTarget,
                at = FIXTURE_NOW,
            ),
        ).state
        val backAt: Instant =
            awaitingALanding.surveys.first { it.target == probeTarget }.completesAt + 30.minutes
        val landed: GameState = advance(awaitingALanding, from = FIXTURE_NOW, to = backAt)
        val foundWorld: World = landed
            .worldsOf(SystemSelection(galaxy = probeTarget.galaxy, system = probeTarget.system))
            .single()

        // ── the scans the fixtures above are built from ──────────────────────────────────────

        fun GameState.unmistakable(): List<Pair<GalaxyCoordinate, String>> {
            val names = galaxy.surveyed.map { worldNameAt(galaxy.seed, it) }
            return galaxy.surveyed
                .map { it to worldNameAt(galaxy.seed, it) }
                .filter { (_, name) -> names.count { name in it } == 1 }
        }

        fun GameState.tripTo(at: GalaxyCoordinate) = FleetBalance.roundTrip(from = galaxy.home, to = at)

        fun GameState.yieldOf(at: GalaxyCoordinate): Int =
            GalaxyBalance.yieldScore(checkNotNull(worldAt(galaxy.seed, at)).traits).perMillion

        fun GameState.disagreeingPair(): Pair<String, String> {
            val candidates = unmistakable()
            for ((first, nearerName) in candidates) {
                for ((second, richerName) in candidates) {
                    if (tripTo(first) < tripTo(second) && yieldOf(first) < yieldOf(second)) {
                        return nearerName to richerName
                    }
                }
            }
            error("no two worlds in the ledger that `nearest` and `richest` order differently")
        }

        // Nearest first, so the probe in the discovery fixtures is a flight a player would really
        // buy. Exactly one world, because a landing on a system holding several degrades the card to
        // the compact form and prints the same stamp on each of them — one node is what an assertion
        // about a stamp needs.
        fun GameState.aSystemHoldingOneUnsurveyedWorld(): SystemAddress {
            val home = galaxy.home
            return (1..GalaxyBalance.SYSTEMS_PER_GALAXY)
                .sortedBy { abs(it - home.system) }
                .map { SystemAddress(galaxy = home.galaxy, system = it) }
                .first { address ->
                    val worlds = worldsOf(SystemSelection(galaxy = address.galaxy, system = address.system))
                    worlds.size == 1 && worlds.none { it.at in galaxy.surveyed }
                }
        }
    }
}
