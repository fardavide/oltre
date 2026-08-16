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
    const val COORDINATE = "galaxy-coordinate"
    const val HOME = "galaxy-home"

    // ── The two maps, which are different drawings of different things ───────────────────────
    //
    // `SYSTEM_MAP` is the orbit card — fifteen slots of one system. `GALAXY_MAP` is the fold —
    // 250 systems in ten banded regions, and the screen the tab lands on since 0.12. They were one
    // constant called `MAP` until the fold arrived, and one name for two drawings is exactly the
    // ambiguity a tag exists to prevent.
    const val SYSTEM_MAP = "galaxy-system-map"
    const val GALAXY_MAP = "galaxy-map"

    // The four discs, and one of them. A disc is tapped as a whole — a summary, not a drawing you
    // aim at — so the tag is on the card rather than on anything inside it.
    const val UNIVERSE = "galaxy-universe"

    // The bar under the fold: the map's one readout and, when it carries a probe, the map's one
    // control. `CAPTION_ACTION` is present exactly when a probe is dispatchable from here, which is
    // the same assertion `DISPATCH` carries on the orbit page.
    const val CAPTION = "galaxy-caption"
    const val CAPTION_ACTION = "galaxy-caption-action"

    // The chip at the right of the map's header — the only way up to the universe and the only way
    // back down. Not a tab and not a push: it swaps two states of one surface.
    const val SCALE_CHIP = "galaxy-scale-chip"

    // ── The ledger, and the head above every view ────────────────────────────────────────────
    //
    // The Galaxy tab lands on the *map* since 0.12, so these name the controls of the list you get
    // to from it. The filter chips and the sort control went with that change — a filter narrows a
    // list, and "where next" was never a list question.
    const val LEDGER_SEARCH = "galaxy-ledger-search"

    // The region name in the system header: the only accent string there, and the way back out to
    // the fold, framed on the system you were looking at. It named the region index until 0.12 —
    // same pixels, different target.
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

    // **The dispatch sheet's own tags left with the sheet** — they are `DispatchTestTags` in
    // `:client:dispatch:ui` now, because the sheet is raised from Fleets as well and a handle
    // reading `galaxy-` would name the wrong screen half the time. `DISPATCH` above stays: it is the
    // *probe* button in the map card's footer, which is this tab's and aimed at a star rather than
    // at a world.

    // **The whole address, because the ledger lists six systems at once** and a tag keyed by the slot
    // named one node per system in the list — an ambiguity a test could only work around by staying
    // in the system view, which is not the screen the tab opens on.
    fun row(at: GalaxyCoordinate): String = "galaxy-row-${at.galaxy}-${at.system}-${at.slot}"

    fun mode(mode: LedgerMode): String = "galaxy-mode-${mode.name.lowercase()}"

    // One per galaxy in the universe view, and the same four numbers the orbit page's segmented
    // control uses — a galaxy is a galaxy whichever surface names it.
    fun disc(galaxy: Int): String = "galaxy-disc-$galaxy"

    fun galaxy(galaxy: Int): String = "galaxy-tab-$galaxy"

    // Keyed by the world *and* the ladder rather than by the string it renders. The enum rather than
    // the label because "Gravitic 9" is a level away from "Gravitic 8", and a tag that moved with
    // the level would retarget itself every time the empire climbed. The world because a system
    // routinely holds several worlds wanting the same ladder — the seed's own home system holds
    // three — so the ladder alone would name three targets rather than one.
    fun adaptation(at: GalaxyCoordinate, technology: AdaptationTechnology): String =
        "galaxy-adaptation-${at.galaxy}-${at.system}-${at.slot}-${technology.name.lowercase()}"
}
