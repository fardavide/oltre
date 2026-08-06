package dev.fardavide.oltre.client.colony.presentation

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
fun oltreRoborazziOptions(): RoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.007f),
        resultValidator = ThresholdValidator(0.08f),
    ),
)
