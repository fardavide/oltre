package dev.fardavide.oltre.client.design.testing

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

// Calibrated against CI diff artifacts (runs 31072340252, 31075759250): macOS-recorded
// baselines rendered on Linux differ by ±1/255 across gradient fills (dithering) and by
// ≥10/255 on 2.4–5.6% of pixels (glyph anti-aliasing — the ratio rises as the screenshot
// shrinks, because text dominates a small canvas). maxDistance 0.007 ignores the
// sub-perceptual dithering noise; the 8% changed-pixel budget covers glyph-edge drift on the
// smallest text-dense component with margin. Raise either only with a new CI diff image as
// evidence.
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
        resultValidator = ThresholdValidator(0.08f),
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
