package dev.fardavide.oltre.client.fleets.ui

import dev.fardavide.oltre.core.ResourceKind

// **What the Fleets tab draws, and nothing about how it is derived.** The fold over `GameState` and
// the event log that produces these is `:client:fleets:presentation`, which depends on this module
// rather than the other way round.

// **Several runs can be in flight at once and nothing listed them.** Since 0.7.0 the Colony strip has
// said `2 more away` — a door with nothing behind it — and this is what is behind it.
//
// Two sections and they answer two different questions: what is out, and what came back. The second
// is a fold over `Event.FleetReturned` and costs no state at all, which is the first player-facing
// use the event log has ever had.
data class FleetsUiState(
    // "5 of 6 away" beside the section rule. The fleet as one number, exactly as the Shipyard states
    // it — the two tabs are two readings of the same pool and must not be able to disagree.
    val away: String,
    val runs: List<RunCardUiState>,
    val landed: List<LandingUiState>,
)

data class RunCardUiState(
    val coordinate: String,
    // "1 skiff · 132 metal" — the manifest and what it is bringing, which are the two things that
    // make one run distinguishable from another at a glance.
    val manifest: String,
    val countdown: String,
    // "home 22:41" — the wall-clock instant the hulls are back, so a player deciding whether to wait
    // has a time of day rather than only a duration.
    val lands: String,
    // "out 10m · on station 2h 40m · home 10m". The three legs, in the order they happen.
    val legs: String,
    val compactLegs: String,
    val phase: RunPhase,
    val bar: RunBarUiState,
)

// **Derived in presentation from `dispatchedAt + flight`, so `core` keeps storing one instant rather
// than three.** Design's seventh call, and the reason `FleetRun.flightEndsAt` exists and is read by
// nothing in `advance`: a run has exactly one transition and it is the return, so the two boundaries
// a player can see are a rendering rather than a rule.
enum class RunPhase { OUTBOUND, ON_STATION, INBOUND }

// One bar, three phases, two hairline ticks where the flight ends and begins again. All three are
// fractions of the whole window rather than of the phase, because the bar is one length: a tick is
// where the leg boundary *is*, not how far through a leg the run has got.
data class RunBarUiState(
    val progress: Float,
    val outboundEndsAt: Float,
    val inboundBeginsAt: Float,
)

// A run that has already landed. The clock is local and the day is not, which is why the stamp is a
// string rather than an instant: "yest." is a fact about the reader's calendar and the ledger is the
// only place in the app that needs one.
data class LandingUiState(
    val stamp: String,
    val coordinate: String,
    val amount: String,
    val kind: ResourceKind,
)
