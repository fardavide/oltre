package dev.fardavide.oltre.client.design.testing

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

// **The 8% budget was never slack. It was a tight fit around a difference that had no business
// existing — and the fix is to delete the difference, not to keep budgeting for it.**
//
// Measured on 2026-08-21, against the whole corpus rather than one CI artifact. With
// `maxDistance = 0.007`, re-rendering `main`'s committed baselines on a Mac reports **5–7.5% of
// pixels changed on nearly every one of the 102 frames**, and two of them spill past 8% outright —
// `facility_sheet_locked` at 9.4% and `research_sheet_inert_slide_over` at 8.7%, the two frames
// carrying the most wrapped prose. That is macOS-versus-Linux glyph rasterisation, it is pervasive,
// and 0.08 sat just above the bulk of it with no room left.
//
// **What that cost was not red builds — it was the loop.** Two frames over the line meant a local
// `recordRoborazziDesktop` produced a corpus that failed on Linux CI, so visual changes went out
// through the manual "Record screenshots" job instead: dispatch, wait, take its commit,
// re-dispatch CI. PR #92 spent **38 workflow runs in one day** that way (30 CI, 8 record
// dispatches).
//
// **The difference is entirely the two operating systems, and nothing else.** Two consecutive
// `recordRoborazziDesktop` runs on the same Mac produced **byte-identical output for all 102
// frames** — rendering is deterministic per machine. So one OS at both ends leaves nothing for a
// budget to absorb, which is why CI's screenshot job runs on macOS (see `ci.yml`) and why this is
// now 0f: not one pixel may differ beyond the per-pixel floor. `ThresholdValidator` compares
// `pixelDifferences <= roundToInt(pixelCount * threshold)`, so 0f is a clean "zero changed pixels"
// rather than an off-by-one.
//
// `maxDistance` stays at 0.007 and keeps doing the one job that is still real: at 0 every frame
// comes back changed on 0.5–33.5% of its pixels from sub-perceptual dithering (±1/255 across
// gradient fills). It is below perception and above that floor.
//
// **Raising either number is not a fix for a red baseline.** With one renderer, a red frame means
// the drawing moved or the two Macs disagree, and both are findings. Widen only with a diff image
// and a note here saying which — and note there is one copy, so loosening it loosens every baseline
// in the repo at once.
//
// This was copied into three modules' `desktopTest` before 0.0.14 — the threshold `decisions.md`
// set for extracting it. Sharing it needs a module because KMP source sets cannot host test
// fixtures, so this is the whole reason `:client:design:testing` exists. The duplication was
// dangerous in a specific way: two numbers that must be raised *together and only with evidence*
// were maintained in three places, so the cheapest way to silence a failing baseline was to loosen
// one copy and leave the other two saying something else.
fun oltreRoborazziOptions(): RoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.007f),
        resultValidator = ThresholdValidator(0.0f),
    ),
)

// How far every screenshot test winds the paused clock forward before it opens the shutter.
//
// The Sky pass gave the app four one-shot transitions, and a baseline caught in the middle of one
// is the only way any of them can flake: the fills take 900ms and the completion band leaves the
// card at 1,170ms, so a capture at an arbitrary frame between those records a half-drawn arc that
// the next run will not reproduce. Every screenshot test therefore stops the clock before
// `setContent` and advances it by this, so what a baseline holds is the settled screen — the one a
// player is looking at a moment after opening the app, and the one they go on looking at.
//
// Comfortably past the last of them rather than exactly on it, because "exactly on it" is a
// boundary and boundaries are what round differently on two machines.
const val SETTLED_MILLIS: Long = 2_000
