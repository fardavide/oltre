package dev.fardavide.oltre.client.colony.presentation

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

// Calibrated against CI diff artifacts (run 31072340252): macOS-recorded baselines rendered on
// Linux differ by ±1/255 across gradient fills (dithering) and by ≥10/255 on 2.4–4.3% of
// pixels (glyph anti-aliasing). maxDistance 0.007 ignores the sub-perceptual dithering noise;
// the 5% changed-pixel budget covers glyph-edge drift with margin. Raise either only with a
// new CI diff image as evidence.
fun oltreRoborazziOptions(): RoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.007f),
        resultValidator = ThresholdValidator(0.05f),
    ),
)
