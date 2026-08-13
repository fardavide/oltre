package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.core.ResourceKind

// **Numbers by hand, the way every screenshot fixture in this repository states them.** A baseline
// should move when the screen moves and never when a balance constant does, so these carry their own
// figures rather than deriving them from a `GameState`. The three runs are Design's own frame —
// `[3:165:13]` inbound, `[3:185:4]` on station and `[3:165:8]` outbound — re-homed onto the seed's
// real system, which moved to 3:171 at 0.5.1.

// The frame the whole design decision is for: three runs, one in each phase, so the three-hue
// vocabulary is in one picture and the two hairline ticks are visible at three different offsets.
internal val threeRunsUiState = FleetsUiState(
    away = "5 of 6 away",
    runs = listOf(
        RunCardUiState(
            coordinate = "[3:171:13]",
            manifest = "1 skiff · 52 crystal",
            countdown = "00:08:41",
            lands = "home 14:22",
            legs = "out 13m · on station 5h 34m · home 13m",
            compactLegs = "13m · 5h 34m · 13m",
            phase = RunPhase.INBOUND,
            bar = RunBarUiState(progress = 0.97f, outboundEndsAt = 0.036f, inboundBeginsAt = 0.964f),
        ),
        RunCardUiState(
            coordinate = "[3:185:4]",
            manifest = "3 skiffs · 882 crystal",
            countdown = "08:12:44",
            lands = "home 19:04",
            legs = "out 29m · on station 11h 02m · home 29m",
            compactLegs = "29m · 11h 02m · 29m",
            phase = RunPhase.ON_STATION,
            bar = RunBarUiState(progress = 0.32f, outboundEndsAt = 0.040f, inboundBeginsAt = 0.960f),
        ),
        RunCardUiState(
            coordinate = "[3:171:8]",
            manifest = "1 skiff · 132 metal",
            countdown = "02:41:07",
            lands = "home 22:41",
            legs = "out 10m · on station 2h 40m · home 10m",
            compactLegs = "10m · 2h 40m · 10m",
            phase = RunPhase.OUTBOUND,
            bar = RunBarUiState(progress = 0.04f, outboundEndsAt = 0.056f, inboundBeginsAt = 0.944f),
        ),
    ),
    landed = listOf(
        LandingUiState(stamp = "11:04", coordinate = "[3:185:4]", amount = "+588 crystal", kind = ResourceKind.CRYSTAL),
        LandingUiState(stamp = "07:22", coordinate = "[3:171:8]", amount = "+281 metal", kind = ResourceKind.METAL),
        LandingUiState(stamp = "yest.", coordinate = "[3:171:13]", amount = "+113 crystal", kind = ResourceKind.CRYSTAL),
        LandingUiState(stamp = "yest.", coordinate = "[3:171:8]", amount = "+132 metal", kind = ResourceKind.METAL),
    ),
)

// The first sitting: the granted skiff, out for the first time, and nothing has ever come back — so
// the ledger is absent rather than an empty heading.
internal val firstRunUiState = FleetsUiState(
    away = "1 of 1 away",
    runs = listOf(
        RunCardUiState(
            coordinate = "[3:171:8]",
            manifest = "1 skiff · 66 metal",
            countdown = "02:41:07",
            lands = "home 10:41",
            legs = "out 10m · on station 2h 40m · home 10m",
            compactLegs = "10m · 2h 40m · 10m",
            phase = RunPhase.OUTBOUND,
            bar = RunBarUiState(progress = 0.04f, outboundEndsAt = 0.056f, inboundBeginsAt = 0.944f),
        ),
    ),
    landed = emptyList(),
)

// **The one state on this screen with no frame behind it.** A new colony has an idle pool and
// nothing out, so this is what the tab says on a first launch — drawn in the idiom the Shipyard's
// footnote already spends rather than invented.
internal val nothingOutUiState = FleetsUiState(
    away = "0 of 1 away",
    runs = emptyList(),
    landed = emptyList(),
)
