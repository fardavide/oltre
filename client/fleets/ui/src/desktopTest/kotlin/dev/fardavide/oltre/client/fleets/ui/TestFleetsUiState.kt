package dev.fardavide.oltre.client.fleets.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.Gravity
import dev.fardavide.oltre.core.Hazard
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Temperature

// **Numbers by hand, the way every screenshot fixture in this repository states them.** A baseline
// should move when the screen moves and never when a balance constant does, so these carry their own
// figures rather than deriving them from a `GameState`. The three runs are Design's own frame —
// `[3:165:13]` inbound, `[3:185:4]` on station and `[3:165:8]` outbound — re-homed onto the seed's
// real system, which moved to 3:171 at 0.5.1.

// **Claude Design's own day 21**, re-homed onto the seed's real system: five worlds, eleven runs and
// three earlier ones with nowhere to send you. Read down the faces — a banded hot world with a
// radiation halo, a heavy grey rock, a frozen one, a small temperate one, a tidally locked one — and
// the eye recognises before it reads, which is the whole argument for the fold.
internal val day21 = WorkedListUiState(
    trailing = TextRes("11 runs · newest first"),
    compactTrailing = TextRes("11 runs"),
    rows = listOf(
        worked(
            at = GalaxyCoordinate(3, 185, 4),
            name = "Tashkir IV",
            runs = "2 runs",
            total = "1,176 crystal",
            kind = ResourceKind.CRYSTAL,
            deposit = "1,240",
            // The only conditional element on a row, and the reason this frame carries exactly one:
            // it appears when the last landing falls inside the span the launch advanced.
            landed = "landed 11:04",
            celsius = 84,
            milliG = 1_120,
            milliAtm = 5_100,
            hazards = setOf(Hazard.RADIATION_BELT),
        ),
        worked(
            at = GalaxyCoordinate(3, 171, 8),
            name = "Calianova VIII",
            runs = "4 runs",
            total = "611 metal",
            kind = ResourceKind.METAL,
            deposit = "2,240",
            celsius = -8,
            milliG = 1_790,
            milliAtm = 900,
        ),
        // **The vein is finished and the run that finished it is still in the air.** This is the fact
        // the per-run ledger had no slot for, and it is the difference between a door and a door to
        // nothing.
        worked(
            at = GalaxyCoordinate(3, 171, 13),
            name = "Calianova XIII",
            runs = "3 runs",
            total = "388 crystal",
            kind = ResourceKind.CRYSTAL,
            deposit = "empty",
            isEmpty = true,
            celsius = -166,
            milliG = 1_600,
            milliAtm = 3_180,
            hazards = setOf(Hazard.ION_STORMS),
        ),
        worked(
            at = GalaxyCoordinate(3, 172, 2),
            name = "Sorelis II",
            runs = "1 run",
            total = "96 metal",
            kind = ResourceKind.METAL,
            deposit = "full",
            celsius = 12,
            milliG = 880,
            milliAtm = 1_200,
            hazards = setOf(Hazard.SEISMIC_INSTABILITY),
        ),
        worked(
            at = GalaxyCoordinate(3, 96, 9),
            name = "Ashkur IX",
            runs = "1 run",
            total = "204 crystal",
            kind = ResourceKind.CRYSTAL,
            deposit = "3,120",
            celsius = -54,
            milliG = 1_340,
            milliAtm = 2_100,
            hazards = setOf(Hazard.TIDALLY_LOCKED),
        ),
    ),
    unrecorded = TextRes("3 earlier runs · 402 metal · no target recorded"),
)

@Suppress("LongParameterList")
private fun worked(
    at: GalaxyCoordinate,
    name: String,
    runs: String,
    total: String,
    kind: ResourceKind,
    deposit: String,
    isEmpty: Boolean = false,
    landed: String? = null,
    celsius: Int,
    milliG: Int,
    milliAtm: Int,
    hazards: Set<Hazard> = emptySet(),
): WorkedWorldUiState = WorkedWorldUiState(
    at = at,
    name = TextRes(name),
    portrait = WorldPortraitUiState.Surveyed(
        temperature = Temperature(celsius),
        gravity = Gravity(milliG),
        pressure = Pressure(milliAtm),
        hazards = hazards,
        // Means nothing, and that is the point — see `WorldPortraitUiState`. Off in every frame, so
        // a baseline never turns on a mark the mapper did not ask for.
        hasRing = false,
    ),
    total = TextRes(total),
    kind = kind,
    prefix = TextRes("[${at.galaxy}:${at.system}:${at.slot}] · $runs"),
    compactPrefix = TextRes(runs),
    deposit = TextRes(deposit),
    depositIsEmpty = isEmpty,
    landed = landed?.let { TextRes(it) },
)

// The frame the whole design decision is for: three runs, one in each phase, so the three-hue
// vocabulary is in one picture and the two hairline ticks are visible at three different offsets.
internal val threeRunsUiState = FleetsUiState(
    away = TextRes("5 of 6 away"),
    runs = listOf(
        RunCardUiState(
            coordinate = TextRes("[3:171:13]"),
            manifest = TextRes("1 skiff · 52 crystal"),
            countdown = TextRes("00:08:41"),
            lands = TextRes("home 14:22"),
            legs = TextRes("out 13m · on station 5h 34m · home 13m"),
            compactLegs = TextRes("13m · 5h 34m · 13m"),
            phase = RunPhase.INBOUND,
            bar = RunBarUiState(progress = 0.97f, outboundEndsAt = 0.036f, inboundBeginsAt = 0.964f),
        ),
        RunCardUiState(
            coordinate = TextRes("[3:185:4]"),
            manifest = TextRes("3 skiffs · 882 crystal"),
            countdown = TextRes("08:12:44"),
            lands = TextRes("home 19:04"),
            legs = TextRes("out 29m · on station 11h 02m · home 29m"),
            compactLegs = TextRes("29m · 11h 02m · 29m"),
            phase = RunPhase.ON_STATION,
            bar = RunBarUiState(progress = 0.32f, outboundEndsAt = 0.040f, inboundBeginsAt = 0.960f),
        ),
        RunCardUiState(
            coordinate = TextRes("[3:171:8]"),
            manifest = TextRes("1 skiff · 132 metal"),
            countdown = TextRes("02:41:07"),
            lands = TextRes("home 22:41"),
            legs = TextRes("out 10m · on station 2h 40m · home 10m"),
            compactLegs = TextRes("10m · 2h 40m · 10m"),
            phase = RunPhase.OUTBOUND,
            bar = RunBarUiState(progress = 0.04f, outboundEndsAt = 0.056f, inboundBeginsAt = 0.944f),
        ),
    ),
    worked = day21,
    dispatch = null,
)

// The first sitting: the granted skiff, out for the first time, and nothing has ever come back — so
// the ledger is absent rather than an empty heading.
internal val firstRunUiState = FleetsUiState(
    away = TextRes("1 of 1 away"),
    runs = listOf(
        RunCardUiState(
            coordinate = TextRes("[3:171:8]"),
            manifest = TextRes("1 skiff · 66 metal"),
            countdown = TextRes("02:41:07"),
            lands = TextRes("home 10:41"),
            legs = TextRes("out 10m · on station 2h 40m · home 10m"),
            compactLegs = TextRes("10m · 2h 40m · 10m"),
            phase = RunPhase.OUTBOUND,
            bar = RunBarUiState(progress = 0.04f, outboundEndsAt = 0.056f, inboundBeginsAt = 0.944f),
        ),
    ),
    worked = null,
    dispatch = null,
)

// **The one state on this screen with no frame behind it.** A new colony has an idle pool and
// nothing out, so this is what the tab says on a first launch — drawn in the idiom the Shipyard's
// footnote already spends rather than invented.
internal val nothingOutUiState = FleetsUiState(
    away = TextRes("0 of 1 away"),
    runs = emptyList(),
    worked = null,
    dispatch = null,
)
