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
