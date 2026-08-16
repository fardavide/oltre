package dev.fardavide.oltre.client.fleets.ui

import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind

// **What the Fleets tab draws, and nothing about how it is derived.** The fold over `GameState` and
// the event log that produces these is `:client:fleets:presentation`, which depends on this module
// rather than the other way round.

// **Several runs can be in flight at once and nothing listed them.** Since 0.7.0 the Colony strip has
// said `2 more away` — a door with nothing behind it — and this is what is behind it.
//
// Two sections and they answer two different questions: what is out, and where you have been. The
// second is a fold over `Event.FleetReturned` and costs no state at all, which is the first
// player-facing use the event log has ever had.
data class FleetsUiState(
    // "5 of 6 away" beside the section rule. The fleet as one number, exactly as the Shipyard states
    // it — the two tabs are two readings of the same pool and must not be able to disagree.
    val away: String,
    val runs: List<RunCardUiState>,
    // Absent until something has come home. A heading over nothing is a section claiming there is a
    // history when there is not — the same rule `runs` follows in the other direction.
    val worked: WorkedListUiState?,
    // Null until a row is tapped. The sheet is this feature's own navigation exactly as it is
    // Galaxy's, so it arrives here rather than being a second screen the shell would know about.
    val dispatch: DispatchUiState?,
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

// **The list stops being made of runs and starts being made of worlds** — Claude Design's one move,
// 2026-08-16, and the whole of what makes the section a door rather than a receipt. Eleven runs are
// five worlds; folding them lets a row carry what a single landing never had (how many times you
// went, what the world has paid in total, and whether there is anything left), and it stops the list
// printing `[3:165:8]` twice and asking the player to do the folding.
//
// **The five-event cap retired with it.** A roll-up is its own cap — an empire's worth of runs is a
// handful of worlds — so the totals read the whole log, which they have to or they are wrong.
data class WorkedListUiState(
    // "11 runs · newest first", and 320dp keeps the count and drops the ordering — the rows are in
    // an order the eye can see, so the words are the part that can go.
    val trailing: String,
    val compactTrailing: String,
    val rows: List<WorkedWorldUiState>,
    // "3 earlier runs · 402 metal · no target recorded". One faint line at the foot, with no disc —
    // **and the missing disc is the whole mechanism**, because the disc is what says *this opens*.
    // A landing with no target is not a world, so in a list of worlds it cannot be a row.
    val unrecorded: String?,
)

// One world your fleet has worked. **Identity is the name and the disc**, both free and both already
// the Galaxy row's; beyond identity the row carries three moving facts and no more — how many runs,
// what has landed in total, and what is left in the ground.
//
// What Design rejected, and why each is absent: the manifest that went (a fact about what was idle
// that day, and the sheet will not pre-fill it anyway), the time on station (a property of the window
// you picked), and richness and the round trip — both fixed for the life of the world, and both
// printed by the sheet one tap later.
data class WorkedWorldUiState(
    // What the tap raises a sheet on. The whole coordinate, for `DispatchSelection`'s reason: this
    // list holds worlds from everywhere and a slot alone would be completed from somewhere else.
    val at: GalaxyCoordinate,
    val name: String,
    val portrait: WorldPortraitUiState.Surveyed,
    // "1,176 crystal", in that resource's own hue — the lifetime total, at the right of line one.
    val total: String,
    // Colours the total and the word on line two. One resource per world: whichever it has actually
    // paid the most of, which is the one you went back for.
    val kind: ResourceKind,
    // "[3:185:4] · 2 runs", the faint head of line two. **320dp drops the coordinate, not the
    // deposit**: the name is the identity now, the address is verification, and the address is in the
    // sheet's own head one tap later. The same call as `Deuterium Synth.` — a width decision, not a
    // change of voice.
    val prefix: String,
    val compactPrefix: String,
    // "1,240", "full", "empty" — 0.9's deposit idiom unchanged, and the one reading on the row that
    // can say a door leads nowhere.
    val deposit: String,
    val depositIsEmpty: Boolean,
    // "landed 11:04", and the only conditional element on the row: present when the last landing
    // falls inside the span this launch advanced, which is the same derivation the discovery card
    // makes — so no seen-flag and no new stored state. **It carries the verb**, because a bare clock
    // in Oltre is a countdown.
    val landed: String?,
)
