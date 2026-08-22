package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRobot
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
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test

// **The Galaxy tab, driven the way a finger drives it.** Everything here goes through
// `galaxyScreen`, the stateful harness, and never through a handed frame — the view, the query and
// the discovery boundary are all `GalaxyScreen`'s own `remember`s, so a *tap that changes one of
// them* is a claim only this harness can make. `LedgerUiStateTest` already asserts what the mapper
// computes; what is missing without this file is that touching the control reaches the mapper at
// all.
//
// **The worlds list is one tap in since 0.12, and every test below pays that tap out loud.** The tab
// lands on the drawn galaxy now, so a test about the list has to cross the switch to reach one — and
// folding the crossing into the harness was rejected, because *"the list is one tap away"* is the
// design's own claim about what the new default costs. A helper that hid the tap would be hiding the
// price, and the file would go on passing the day the switch stopped working.
//
// **No coordinate and no name is written down.** The galaxy is generated, so `Calanova VIII` would be
// an assertion about the seed rather than about the ledger, and it would go quietly vacuous the day
// genesis moves — which it did at 0.5.1. Every fixture below scans the survey set instead, which
// picks the same world on every run and still picks *a* world after a reseed.
@OptIn(ExperimentalTestApi::class)
class LedgerBehaviourTest {

    @Test
    fun `the tab opens on the drawn galaxy`() {
        // **This inverts 0.11.0's headline, and Claude Design overruled itself to invert it.** That
        // slice landed on the worlds you already know, arguing that runs go out several times a day
        // where probes go once or twice, so the commoner errand should have the chair. What the
        // frequency argument missed is *where else each screen can be read*: *"the galaxy exists
        // nowhere else in the app, and the worlds you hold are already on Colony and on Fleets"* — so
        // the old default spent the tab's one landing slot on the one thing that was not exclusive
        // to it.
        //
        // Both halves are asserted because the tab draws two maps and only one of them is a landing:
        // the orbit page is the tab's one real push, so opening on it would be opening somewhere you
        // then have to come back from. Nothing is tapped in this block, and that is the test.
        galaxyScreen(state = wellTravelledState) {
            assertTheGalaxyIsDrawn()
            assertNoSystemIsDrawn()
        }
    }

    @Test
    fun `the tab comes back to whichever of the two lists was left up`() {
        // **The one line of this tab that reaches disk**, and Davide's call of 2026-08-15 overruling
        // Claude Design's own rule that nothing here does — *"a filter that outlives the check-in
        // that set it is a screen lying about what it holds"*. The exemption is narrow and the
        // argument is about the hundredth check-in rather than the first: landing on the map is right
        // when you have nowhere to go, and wrong forever after if the map is not where you go.
        //
        // Both ends in one test, because neither is the claim on its own. A screen that reported a
        // landing nothing could open on, or opened on one nothing ever reported, would satisfy a test
        // of either half and lose the preference on the way to disk.
        val reported = mutableListOf<GalaxyLanding>()

        galaxyScreen(state = wellTravelledState, onLandingChange = { reported += it }) {
            openTheLedger()
        }

        assertEquals(listOf(GalaxyLanding.WORLDS), reported.toList())

        galaxyScreen(state = wellTravelledState, landing = GalaxyLanding.WORLDS) {
            assertNoGalaxyIsDrawn()
            // The head counts what the body lists, and with no query that is the whole survey set —
            // the set the identity slice exists to make valuable.
            assertReads("${wellTravelledState.galaxy.surveyed.size} WORLDS")
            assertTheLedgerLists(nearest)
        }
    }

    @Test
    fun `the switch crosses to the map and back and what was typed survives the trip`() {
        // The list is one tap from the map and the map is one tap back, which is what bought the
        // default: no tab was added and nothing else on the screen moved. The query surviving the
        // crossing is the other half — the field is **always visible and never a mode**, so coming
        // back is not retyping.
        galaxyScreen(state = wellTravelledState) {
            openTheLedger()
            search(ambiguousName)
            assertReads("1 WORLD")

            openTheMap()
            assertTheGalaxyIsDrawn()

            openTheLedger()
            assertNoGalaxyIsDrawn()
            assertReads("1 WORLD")
        }
    }

    @Test
    fun `the map is still one tap away when the query has emptied the ledger`() {
        // The head sits above all four bodies and is gated by none of them. A query that finds
        // nothing is exactly where a player most wants the other view, so a switch that lived inside
        // the list would have taken the way out away at the one moment it was worth having.
        galaxyScreen(state = wellTravelledState) {
            openTheLedger()
            search(UNSPELLABLE)
            assertReads("No world you know is called that.")

            openTheMap()
            assertTheGalaxyIsDrawn()
        }
    }

    // ── Search, the literal answer to "pagine gialle in the 90s" ─────────────────────────────

    @Test
    fun `typing a system's name leaves that system's worlds and drops every other`() {
        // A world's name is its system's name plus a numeral, so one query answers *show me that
        // place* — the one thing on this tab where typing beats tapping, and the reason the field is
        // never behind a mode.
        galaxyScreen(state = wellTravelledState) {
            openTheLedger()
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
            openTheLedger()
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
            openTheLedger()
            search(UNSPELLABLE)

            assertReads("NO WORLDS")
            assertReads("No world you know is called that.")
            assertReads("Names are unique in a galaxy, so a full name finds one place.")
            assertNothingIsListed(nearest)
        }
    }

    // ── The pins, which are what the list kept ───────────────────────────────────────────────

    @Test
    fun `a pinned world is listed above the rest under its own heading and never twice`() {
        // The one thing on this screen that reaches the *save* — the landing above reaches a
        // preferences file, which is not the same thing. The pin is put on the world with the
        // **longest** round trip, which `nearest first` belongs at the very bottom of the list — so
        // finding it above the nearest row is the pin doing it and not the order. That order is a
        // fact about the list rather than one of four you could pick since 0.12 dropped the sort,
        // which is what makes the bottom of the list a place a fixture can rely on.
        //
        // A pin changes *where a thing is*, which is what a pin means; drawn in both sections it
        // would instead be a row the player has to notice is the same row twice.
        galaxyScreen(state = pinnedFarthest) {
            openTheLedger()
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
            // The section is a section of the *worlds list*, so reaching it costs the same tap
            // everything else here costs — and the tap is spent before the probe lands so that both
            // halves of the claim are made on one screen. Asserting the absence from the map would
            // be asserting that a surface which never draws discoveries is not drawing one.
            openTheLedger()

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
        //
        // **The tab landing on the map is what turned that into an ordinary check-in rather than a
        // deliberate act.** Until 0.12 a player had to choose to leave the list; now the list is
        // somewhere you go and the map is where you come back to, so the banking crossing is the one
        // a player makes anyway.
        galaxyAcrossACheckIn(opening = awaitingALanding, at = FIXTURE_NOW) { comeBackTo ->
            openTheLedger()
            comeBackTo(landed, backAt)
            assertReads("SURVEYED")

            openTheMap()
            openTheLedger()

            assertNothingReads("SURVEYED")
            // ...and the world is still in the ledger. What expired is the announcement, not the
            // reading — asked for by coordinate because that is the row's key and it is unique where
            // a name shared with a discovery card would not be.
            assertTheLedgerLists(English.resolve(foundWorld.at.label()))
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
                            // The real default, and the tests below tap their way to the list from
                            // it: a check-in that opened straight on the ledger would be a check-in
                            // no preferences file had ever been written by.
                            landing = GalaxyLanding.MAP,
                            onLandingChange = {},
                            onOpenResearch = {},
                            onDispatchProbe = {},
                            onDispatchRun = { _, _, _, _ -> },
                            onToggleAnnounce = {},
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
        val ambiguousAddress: String = English.resolve(ambiguous.first.label())

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

        fun GameState.tripTo(at: GalaxyCoordinate) =
            FleetBalance.roundTrip(
                from = galaxy.home,
                to = at,
                research = research,
                ships = FleetBalance.FASTEST_HULL,
            )

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
