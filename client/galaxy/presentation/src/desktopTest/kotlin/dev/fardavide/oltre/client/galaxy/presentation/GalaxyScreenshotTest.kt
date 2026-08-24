package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.client.galaxy.ui.DESTINATION_HEIGHT
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every view the Galaxy tab has, at both widths the app is baselined for.
//
// **Each frame is the real mapper's output over a real `GameState`** — see `GalaxyFrames`, and the
// three thousand lines of stated fixtures it replaced. Nothing here performs a gesture: a screenshot
// renders a state, and the state is passed in.
@OptIn(ExperimentalTestApi::class)
class GalaxyScreenshotTest {

    // ── The fold, which is what the tab opens on since 0.12 ─────────────────────────────────
    //
    // **The map frames are 650dp tall and the list frames are not**, and the difference is the whole
    // reason this section exists. A list scrolls, so photographing it in a tall window shows more of
    // one screen; the fold does not, so its window *is* the screen and a generous one photographs a
    // device that does not exist.
    //
    // 650 is what a phone leaves a destination — 852 less a 55dp resource rail, a 52dp tab bar and
    // two safe-area insets — and it is the figure 0.12.0 shipped without. Every map frame was 852
    // then, every one of them had room to spare, and the caption was off the bottom of a real screen
    // the whole time.

    @Test
    fun `the map as the tab opens at phone width`() {
        capture(width = 393, height = DESTINATION_HEIGHT, uiState = frame(state = pinnedState), name = "galaxy_map")
    }

    // 98% unsurveyed is the state the map is in nearly always, so it is the frame that had to be good
    // first — and it is the one the old ledger was worst at, being five rows and a header.
    @Test
    fun `the map on day one with nothing surveyed`() {
        capture(width = 393, height = DESTINATION_HEIGHT, uiState = frame(), name = "galaxy_map_genesis")
    }

    // Scrubbed off home, which is the map's own second state: a different band lit, a different name
    // beside a different star, and a caption offering a probe rather than quoting a round trip.
    @Test
    fun `the map with a star selected away from home`() {
        capture(
            width = 393,
            height = DESTINATION_HEIGHT,
            uiState = frame(state = pinnedState, at = pinnedState.homeSelection().copy(system = 195)),
            name = "galaxy_map_selected",
        )
    }

    // **The third tier, selected.** 195 above is inside the hour of grace, so that frame keeps a
    // charted caption and says nothing about fog; this one is the state the whole slice is about —
    // a grain star answering, with its address for a name and the fog yield where the world count
    // would be.
    @Test
    fun `the map with a star selected outside the charted stretch`() {
        capture(
            width = 393,
            height = DESTINATION_HEIGHT,
            uiState = frame(state = pinnedState, at = pinnedState.homeSelection().copy(system = 240)),
            name = "galaxy_map_uncharted",
        )
    }

    // **The orbit page obeying the tier**, which is the frame that says the fog is not just a
    // drawing: the caption's whole bar is a tap target, so this page is one tap from any grain star
    // and would otherwise hand over the name, the region, the class, the worlds and the orbits that
    // the bar had just refused to say.
    @Test
    fun `the orbit page of a star the light has not reached`() {
        capture(
            width = 393,
            height = DESTINATION_HEIGHT,
            uiState = frame(
                state = pinnedState,
                view = GalaxyView.SYSTEM,
                at = pinnedState.homeSelection().copy(system = 240),
            ),
            name = "galaxy_system_uncharted",
        )
    }

    // The amber ring, which is the fleet strip's amber meaning what it means there.
    @Test
    fun `the map with a probe in flight`() {
        capture(width = 393, height = DESTINATION_HEIGHT, uiState = probeInFlightMapUiState, name = "galaxy_map_in_flight")
    }

    @Test
    fun `the four galaxies at phone width`() {
        capture(
            width = 393,
            height = DESTINATION_HEIGHT,
            uiState = frame(state = pinnedState, view = GalaxyView.UNIVERSE),
            name = "galaxy_universe",
        )
    }

    // ── The worlds list, one tap away ────────────────────────────────────────────────────────

    @Test
    fun `the ledger as the tab opens at phone width`() {
        capture(
            width = 393,
            height = 1400,
            uiState = frame(state = pinnedState, view = GalaxyView.WORLDS),
            name = "galaxy_ledger",
        )
    }

    // Five rows and an invitation. The design spends the empty half on the region rather than on an
    // apology, and this is the frame that has to earn that.
    @Test
    fun `the ledger at genesis`() {
        capture(
            width = 393,
            height = 900,
            uiState = frame(view = GalaxyView.WORLDS),
            name = "galaxy_ledger_genesis",
        )
    }

    // `galaxy_ledger_empty` was retired here rather than re-recorded. It photographed *"no world
    // matches all three"* — the state three filter chips could put the list in — and with the chips
    // gone the only remaining emptiness is the genesis one above and a query nothing answers to,
    // which the search frame already carries.

    // The survey moment: a section of the ledger rather than a layer over it, so it is photographed
    // in place rather than as a card of its own.
    @Test
    fun `the ledger with a world found overnight`() {
        capture(
            width = 393,
            height = 1400,
            uiState = frame(state = justSurveyedState, view = GalaxyView.WORLDS, seenAt = JUST_SURVEYED_SINCE),
            name = "galaxy_ledger_discovery",
        )
    }

    // The other half of the discovery card, and the half that had no baseline: one world found
    // rather than several, so the card states its three readings as a labelled column instead of
    // collapsing them onto one line. Which form is drawn is `discoveries.size > 1`, so the two are
    // one tap of a probe apart and a player meets both.
    @Test
    fun `one world found overnight is a labelled column`() {
        capture(
            width = 393,
            height = 1000,
            uiState = frame(
                state = justSurveyedOneWorldState,
                view = GalaxyView.WORLDS,
                seenAt = JUST_SURVEYED_SINCE,
            ),
            name = "galaxy_ledger_discovery_single",
        )
    }

    @Test
    fun `search returns one place`() {
        val name = wellTravelledState.let { state ->
            dev.fardavide.oltre.core.systemNameAt(
                state.galaxy.seed,
                state.galaxy.home.galaxy,
                state.galaxy.home.system,
            )
        }
        capture(
            width = 393,
            height = 900,
            uiState = frame(state = wellTravelledState, view = GalaxyView.WORLDS, query = name.take(6)),
            name = "galaxy_ledger_search",
        )
    }

    // ── The orbit page, which is where you go to acquire a reading you do not have ───────────

    @Test
    fun `the home system at phone width`() {
        capture(
            width = 393,
            // Tall enough for all seven rows. At 1500 the frame cut `Elyotis X` mid-row and slot 11
            // never rendered at all, so the two rows most likely to wrap had no baseline — a
            // screenshot that stops short asserts its own truncation forever.
            height = 1800,
            uiState = frame(view = GalaxyView.SYSTEM),
            name = "galaxy_home_system",
        )
    }

    // The arc, and the only frame that has one: a flight leaves from where it was launched, and every
    // probe in this game is launched from home. Without this the curve, its gradient and the label at
    // its faint end are drawn by nothing and asserted by nothing.
    @Test
    fun `the home system with a probe away`() {
        capture(
            width = 393,
            height = 1800,
            uiState = probeOutFromHomeUiState,
            name = "galaxy_home_system_probe_out",
        )
    }

    @Test
    fun `a system nobody has looked at`() {
        capture(
            width = 393,
            height = 1200,
            uiState = frame(view = GalaxyView.SYSTEM, at = frameState.neighbourSelection()),
            name = "galaxy_unsurveyed",
        )
    }

    // The map card's own lit bell, and the frame that says the two controls are one: this square and
    // the sheet's are the same glyph in the same two states, and they are on opposite sides of their
    // verbs — which is the row's doing rather than an inconsistency, and is exactly the kind of claim
    // a picture settles and a sentence does not.
    @Test
    fun `a system nobody has looked at with the landing asked about`() {
        capture(
            width = 393,
            height = 1200,
            uiState = frame(
                state = frameState.copy(announceFlights = true),
                view = GalaxyView.SYSTEM,
                at = frameState.neighbourSelection(),
            ),
            name = "galaxy_unsurveyed_announced",
        )
    }

    // **The same footer with no bell at all**, which is what a colony opened after 0.18 sees: it asks
    // about alerts by *kind*, so a landing is announced by `Probes` and this control has nothing left
    // to decide. Against the two frames above, the whole diff is that the square is gone and the verb
    // has the row — and a control that vanishes is exactly the change a picture settles and a
    // sentence does not.
    @Test
    fun `a system nobody has looked at when the alerts are asked by kind`() {
        capture(
            width = 393,
            height = 1200,
            uiState = frame(
                state = byCategoryGameState,
                view = GalaxyView.SYSTEM,
                at = frameState.neighbourSelection(),
            ),
            name = "galaxy_unsurveyed_by_category",
        )
    }

    // The one row on the whole screen that is neither a card nor a target: a hairline, the address,
    // the word and the effect. It is drawn by a composable no other frame reaches — one system in
    // forty carries a relay and the home system does not — so without this the demotion from accent
    // settled at 0.0.18 is asserted by a node query and by nothing that can see a colour.
    @Test
    fun `a system carrying a relay`() {
        capture(
            width = 393,
            height = 1100,
            uiState = relaySystemUiState,
            name = "galaxy_relay_system",
        )
    }

    // The region index's two baselines — `galaxy_regions` and `galaxy_regions_slide_over` — are
    // deleted rather than re-recorded. Ten names did not fit on 393dp *of one dimension*, which is
    // the measurement that justified a screen of ten rows; they fit trivially on ten bands, so the
    // measurement stands and its conclusion has expired.

    // ── 320dp, where nothing drops and the lines wrap instead ────────────────────────────────
    //
    // **The map is the same 531dp drawing here as at 393dp**, which is the one measurement this pair
    // of frames exists to keep: the content area is 587dp at 393 and 570dp at 320, so one geometry
    // fits both and there is no compact variant to drift.

    @Test
    fun `the map in a Slide Over window`() {
        capture(
            width = 320,
            height = DESTINATION_HEIGHT,
            uiState = frame(state = pinnedState),
            name = "galaxy_map_slide_over",
        )
    }

    @Test
    fun `the four galaxies in a Slide Over window`() {
        capture(
            width = 320,
            height = DESTINATION_HEIGHT,
            uiState = frame(state = pinnedState, view = GalaxyView.UNIVERSE),
            name = "galaxy_universe_slide_over",
        )
    }

    @Test
    fun `the ledger in a Slide Over window`() {
        capture(
            width = 320,
            height = 1600,
            uiState = frame(state = pinnedState, view = GalaxyView.WORLDS),
            name = "galaxy_ledger_slide_over",
        )
    }

    @Test
    fun `the home system in a Slide Over window`() {
        capture(
            width = 320,
            height = 1700,
            uiState = frame(view = GalaxyView.SYSTEM),
            name = "galaxy_home_system_slide_over",
        )
    }


    // ── The dispatch sheet, and the probe footer it quotes ───────────────────────────────────
    //
    // **These carried baselines before 0.11 and keep them**, which is the point: the sheet and the
    // footer are unchanged by this slice, so a moved pixel in either is a regression rather than a
    // redesign. Four of them are also the README's screens.

    @Test
    fun `the dispatch sheet as it opens`() {
        captureSheet(uiState = dispatchOfferUiState, name = "galaxy_dispatch")
    }

    // **The lit bell, and the only thing that changes is the square.** Booking an alert is the one
    // action in the app whose whole result is that a control changed colour — there is no row to move
    // and no number to update — so it is the one that most wants a frame of its own. Against
    // `galaxy_dispatch` above, this is the whole diff.
    @Test
    fun `the dispatch sheet with the flight asked about`() {
        captureSheet(uiState = dispatchAnnouncedUiState, name = "galaxy_dispatch_announced")
    }

    // **The sheet a colony opened after 0.18 actually gets**, and the third frame of the same sheet
    // for the same reason the second one exists: the whole diff against `galaxy_dispatch` is one
    // control, and here the diff is that the control is *gone*. Under `By category` a run is
    // announced by its kind, so the bell has nothing left to decide and the verb takes the row.
    //
    // The two frames above describe a save carried forward from 0.17, which is why they still have
    // a bell to be lit or unlit — see `testGameState`.
    @Test
    fun `the dispatch sheet when the alerts are asked by kind`() {
        captureSheet(uiState = dispatchByCategoryUiState, name = "galaxy_dispatch_by_category")
    }

    @Test
    fun `the sheet refuses a world nobody has looked at and offers a probe`() {
        captureSheet(uiState = dispatchUnsurveyedUiState, name = "galaxy_dispatch_unsurveyed")
    }

    @Test
    fun `the sheet with every hull away`() {
        captureSheet(uiState = dispatchNoShipsUiState, name = "galaxy_dispatch_no_ships")
    }

    @Test
    fun `the sheet on a world that has been stripped`() {
        captureSheet(uiState = dispatchWaitingUiState, name = "galaxy_dispatch_waiting")
    }

    @Test
    fun `the sheet on a target in another galaxy`() {
        captureSheet(uiState = dispatchFarUiState, name = "galaxy_dispatch_far")
    }

    // The clamp, which is the one place the sheet prints a word where a figure would be: the fleet
    // would lift more than the ground holds, so the headline is the deposit and the slot beside it
    // says so. The clamp note underneath is earned rather than standing — it only exists when some
    // of the hulls sent are contributing nothing — so this is the frame that would catch it going
    // missing or, worse, appearing on every dispatch.
    @Test
    fun `the sheet on a fleet the world cannot fill`() {
        captureSheet(uiState = dispatchWholeDepositUiState, name = "galaxy_dispatch_whole_deposit")
    }

    // The other half of the waiting state, and the reason it is a frame of its own: the stripped
    // world above still has a date, and this ask never will. So the tile that carries "in 2d 02h"
    // is simply absent here, and the sheet ends on a sentence — the one layout in the family whose
    // last row is missing rather than different.
    @Test
    fun `the sheet on an ask no world can ever hold`() {
        captureSheet(uiState = dispatchWaitingForeverUiState, name = "galaxy_dispatch_waiting_forever")
    }

    // Between full and empty, where the deposit chips state a fraction. Both other stock words —
    // "full" and "empty" — are already photographed, so this is the third and last reading the chip
    // has, and the only one whose glyphs can be clipped by a chip sized for a word.
    @Test
    fun `the sheet on a world part worked`() {
        captureSheet(uiState = dispatchWorkedUiState, name = "galaxy_dispatch_worked")
    }

    @Test
    fun `the sheet in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchOfferUiState, name = "galaxy_dispatch_slide_over")
    }

    // **The other two states had no 320dp baseline at all**, and 320 is a width the app is
    // baselined for. Each carries a compact string the offer's frame cannot reach: the waiting
    // state's legs and danger lines are the only compact pair drawn under a countdown rather than a
    // figure, and a refusal is the one layout whose head is the *only* thing that has to fit.
    @Test
    fun `a stripped world in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchWaitingUiState, name = "galaxy_dispatch_waiting_slide_over")
    }

    @Test
    fun `every hull away in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchNoShipsUiState, name = "galaxy_dispatch_no_ships_slide_over")
    }

    @Test
    fun `a probe in flight counts down in the footer`() {
        capture(
            width = 393,
            height = 1200,
            uiState = probeInFlightUiState,
            name = "galaxy_probe_in_flight",
        )
    }

    // **The state every colony opens on, since a probe needs a hull.** Genesis grants none, so this
    // is the first thing the tab says about surveying — and the frame that would catch the metal chip
    // reddening for a shortage that is not metal.
    @Test
    fun `a colony with no scout is told what it needs rather than when`() {
        capture(
            width = 393,
            height = 1200,
            uiState = probeNeedsScoutUiState,
            name = "galaxy_probe_needs_scout",
        )
    }

    @Test
    fun `a scout on its way home turns the refusal into a countdown`() {
        capture(
            width = 393,
            height = 1200,
            uiState = probeScoutComingHomeUiState,
            name = "galaxy_probe_scout_coming_home",
        )
    }

    // ── *Twice the Flight*: the picker, in Design's own three states at both widths ──────────
    //
    // **Nothing is reduced at 320dp and nothing is invented**: the same four sections, the same
    // strings, the same rungs. The hull cells hold two nineteen-character manifests at 320 because
    // their padding is 5dp rather than the card's 11.
    @Test
    fun `the picker at the doorstep, with the hauler in the default`() {
        captureSheet(width = 393, uiState = dispatchPickerUiState, name = "galaxy_picker")
    }

    @Test
    fun `the picker at the doorstep in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchPickerUiState, name = "galaxy_picker_slide_over")
    }

    @Test
    fun `the picker after the skiff cell is tapped`() {
        captureSheet(width = 393, uiState = dispatchPickerSkiffsUiState, name = "galaxy_picker_skiffs")
    }

    @Test
    fun `the picker narrowed at sixty nine systems out, with a locked rung`() {
        captureSheet(width = 393, uiState = dispatchPickerNarrowedUiState, name = "galaxy_picker_narrowed")
    }

    @Test
    fun `the picker narrowed in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchPickerNarrowedUiState, name = "galaxy_picker_narrowed_slide_over")
    }

    @Test
    fun `the rung the hauler just took`() {
        captureSheet(width = 393, uiState = dispatchPickerMovedUiState, name = "galaxy_picker_moved")
    }

    @Test
    fun `the rung the hauler just took in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchPickerMovedUiState, name = "galaxy_picker_moved_slide_over")
    }

    @Test
    fun `the picker on a part-worked vein the hauler alone empties`() {
        captureSheet(width = 393, uiState = dispatchPickerClampedUiState, name = "galaxy_picker_clamped")
    }

    @Test
    fun `a landed probe is a receipt`() {
        capture(width = 393, height = 1200, uiState = probeLandedUiState, name = "galaxy_probe_landed")
    }

    // Every verdict on one screen, which is the frame that would catch one of the six being drawn
    // like another.
    @Test
    fun `every verdict at phone width`() {
        capture(
            width = 393,
            height = 1600,
            uiState = everyVerdictUiState,
            name = "galaxy_every_verdict",
        )
    }

    // ── Italian, at the one width where a longer language has nowhere to go ──────────────────
    //
    // **This tab and the Shipyard are the only two places in the repository where a baseline can see
    // a language at all**, and that is worth knowing before reading the handful below as thin. Twelve
    // of the fifteen screenshot tests photograph hand-written `TextRes("…")` fixtures, which resolve
    // to themselves in every locale — so a full Italian suite over them would assert that English is
    // still English. These frames are the real mapper's output over a real `GameState`, so every word
    // in them comes out of the catalogue.
    //
    // Four rather than every 320dp frame, per Davide's call (2026-08-16): the frames with the tightest
    // text, not all 92. Each of these was picked because a *named* measurement runs through it —
    //
    // - the map's caption drops "from here" past a 54-character monospace budget, and Italian's
    //   "pericolo 2 da qui" is shorter than English's while the star line above it is longer;
    // - the ledger's verdict word sits hard against the right edge, and `Colonizzabile` is thirteen
    //   characters where `Settleable` is ten — the widest single word this translation adds;
    // - the dispatch sheet's legs line is measured to the character, and it is four clauses;
    // - the stripped world draws the only compact legs pair that sits under a countdown.
    //
    // The naming is `<frame>_it.png` beside the English frame rather than a directory of its own, and
    // the `screenshot-testing` skill now says so: a locale baseline is only ever read next to the one
    // it is a translation of, and a sibling directory splits the pair that the diff has to compare.

    @Test
    fun `the map in a Slide Over window in Italian`() {
        capture(
            width = 320,
            height = DESTINATION_HEIGHT,
            uiState = frame(state = pinnedState),
            name = "galaxy_map_slide_over_it",
            translations = Italian,
        )
    }

    @Test
    fun `the ledger in a Slide Over window in Italian`() {
        capture(
            width = 320,
            height = 1600,
            uiState = frame(state = pinnedState, view = GalaxyView.WORLDS),
            name = "galaxy_ledger_slide_over_it",
            translations = Italian,
        )
    }

    @Test
    fun `the sheet in a Slide Over window in Italian`() {
        captureSheet(
            width = 320,
            uiState = dispatchOfferUiState,
            name = "galaxy_dispatch_slide_over_it",
            translations = Italian,
        )
    }

    @Test
    fun `a stripped world in a Slide Over window in Italian`() {
        captureSheet(
            width = 320,
            uiState = dispatchWaitingUiState,
            name = "galaxy_dispatch_waiting_slide_over_it",
            translations = Italian,
        )
    }

    // The sheet is a popup and a popup is a root of its own, so `onRoot` finds two and refuses to
    // choose. The one to photograph is named by what is inside it rather than by the order the two
    // arrive in.
    private fun captureSheet(
        width: Int = 393,
        uiState: GalaxyUiState,
        name: String,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = 852) {
            mainClock.autoAdvance = false
            setContent { OltreTheme(translations) { Surface { Page(uiState) } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNode(isRoot() and hasAnyDescendant(hasTestTag(DispatchTestTags.SHEET))).captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // **English by default rather than the device's language**, which is the one thing a screenshot
    // test must not read: a baseline recorded on an Italian Mac and verified on an English runner
    // would fail on every frame, and for a reason nothing in the diff would explain. The shell reads
    // the locale; a frame is told which language it is about.
    private fun capture(
        width: Int,
        height: Int,
        uiState: GalaxyUiState,
        name: String,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent { OltreTheme(translations) { Surface { Page(uiState) } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // Every callback is empty: a screenshot renders a state, and a frame that could react to a tap
    // would be a frame whose baseline depended on where the mouse was.
    @Composable
    private fun Page(uiState: GalaxyUiState) {
        GalaxyPage(
            uiState = uiState,
            onSelectMode = {},
            onToggleScale = {},
            onQueryChange = {},
            onSelectGalaxy = {},
            onSelectSystem = {},
            onOpenSelected = {},
            onOpenMap = {},
            onGoHome = {},
            onOpenResearch = {},
            onDispatchProbe = {},
            onOpenWorld = {},
            onCloseDispatch = {},
            onSelectGathering = {},
            onSelectShips = {},
            onSelectWindow = {},
            onDispatchRun = {},
            onToggleAnnounce = {},
        )
    }
}
