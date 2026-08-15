package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind

// Keyed by the coordinate rather than by a label, for the reason `ResearchTestTags` is keyed by the
// technology: renaming what a world reads cannot then silently retarget an assertion.
// **Public rather than internal since the layer split**, on `ColonyTestTags`' precedent: the
// robot that reads these lives in `:client:galaxy:ui-testing` now, because two modules' tests
// drive this screen and a test source set is visible to neither.
object GalaxyTestTags {

    const val CONTENT = "galaxy-content"
    const val MAP = "galaxy-map"
    const val COORDINATE = "galaxy-coordinate"
    const val HOME = "galaxy-home"

    // The ±1 steppers went with 0.2.0: the reach band's lens holds the neighbouring system as the
    // cell beside the lit one, which is still one tap and tells you what you are stepping onto
    // before you step.
    const val REACH_STRIP = "galaxy-reach-strip"

    // ── The ledger, and the head above all three views ───────────────────────────────────────
    //
    // The Galaxy tab opens on `worlds` since 0.11, so these name the controls of the *default*
    // screen rather than of a corner of it.
    const val LEDGER_SEARCH = "galaxy-ledger-search"
    const val LEDGER_SORT = "galaxy-ledger-sort"
    const val REGION_INDEX = "galaxy-region-index"

    // The region name in the system header: the only accent string there, and the way into the
    // region index.
    const val REGION = "galaxy-region"

    // The whole footer of the system card, whichever of the six states it is in — so a test can
    // assert *what the card says* without first knowing which state produced it.
    const val PROBE_FOOTER = "galaxy-probe-footer"

    // Only the two states that offer a flight have this, which is the assertion: a screen that
    // never offers a dispatch it would refuse is one where this tag is absent exactly when the
    // model would say no.
    const val DISPATCH = "galaxy-dispatch"

    // The astronomy line under the system header. Stated once because the distance band is
    // identical for all fifteen slots of a system — see `FleetBalance.danger`.
    const val ASTRONOMY = "galaxy-astronomy"

    // ── The dispatch sheet ───────────────────────────────────────────────────────────────────
    //
    // Distinct from `DISPATCH` above, which is the *probe* button in the map card's footer. Two
    // verbs now leave this screen and they are aimed at different things — a probe at a star, a run
    // at a world — so nothing here reuses that tag.
    // On the *contents* rather than on the chrome, so it names the same thing whether a test is
    // driving the real sheet or the contents on their own — `ColonyTestTags.SHEET` is the precedent.
    // There is no tag for the scrim any more: the sheet stopped drawing one when it became an
    // `OltreBottomSheet`, and Material's scrim is the platform's to test, not ours.
    const val SHEET = "galaxy-dispatch-sheet"

    // The verb, present only in the offer state. Absent in every refusal, which is the same
    // assertion `DISPATCH` carries for the probe: a screen that never offers a run the model would
    // refuse is one where this tag is missing exactly when `startRun` would say no.
    const val SEND = "galaxy-dispatch-send"

    // The refusal's own action, which is a different verb in each of the two refusals — a probe
    // where the world is unsurveyed, a countdown where every hull is away. One tag, because what a
    // test wants to know is *what the refusal offers*, not which refusal produced it.
    const val SHEET_ACTION = "galaxy-dispatch-sheet-action"

    const val SHIPS_MORE = "galaxy-dispatch-ships-more"
    const val SHIPS_FEWER = "galaxy-dispatch-ships-fewer"

    fun reachCell(system: Int): String = "galaxy-reach-cell-$system"

    // Keyed by the resource rather than by the label for `adaptation`'s reason: "Metal" is a string
    // the copy owns and `ResourceKind.METAL` is the thing the run actually carries.
    fun gather(kind: ResourceKind): String = "galaxy-dispatch-gather-${kind.name.lowercase()}"

    // Keyed by the rung's own duration in whole minutes, not by the label it prints: a ladder
    // *narrows* on a distant target rather than disabling rungs, so the rung at index 0 is a
    // different window depending on how far away the world is.
    fun window(minutes: Long): String = "galaxy-dispatch-window-$minutes"

    // **The whole address, because the ledger lists six systems at once** and a tag keyed by the slot
    // named one node per system in the list — an ambiguity a test could only work around by staying
    // in the system view, which is not the screen the tab opens on.
    fun row(at: GalaxyCoordinate): String = "galaxy-row-${at.galaxy}-${at.system}-${at.slot}"

    fun mode(mode: LedgerMode): String = "galaxy-mode-${mode.name.lowercase()}"

    // Keyed by the label rather than by the filter, because a chip's *label* is what a player reads
    // and what a robot looks for — and unlike an adaptation level, a filter's label does not move
    // under it between check-ins.
    fun chip(label: String): String = "galaxy-chip-${label.replace(' ', '-')}"

    fun regionRow(region: Int): String = "galaxy-region-row-$region"

    fun galaxy(galaxy: Int): String = "galaxy-tab-$galaxy"

    // Keyed by the world *and* the ladder rather than by the string it renders. The enum rather than
    // the label because "Gravitic 9" is a level away from "Gravitic 8", and a tag that moved with
    // the level would retarget itself every time the empire climbed. The world because a system
    // routinely holds several worlds wanting the same ladder — the seed's own home system holds
    // three — so the ladder alone would name three targets rather than one.
    fun adaptation(at: GalaxyCoordinate, technology: AdaptationTechnology): String =
        "galaxy-adaptation-${at.galaxy}-${at.system}-${at.slot}-${technology.name.lowercase()}"
}
