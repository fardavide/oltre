package dev.fardavide.oltre.client

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

// The same tolerance :client:colony:presentation calibrated against CI diff artifacts — a
// baseline recorded on macOS and verified on Linux differs by ±1/255 across gradient fills and by
// ≥10/255 on a few percent of pixels at glyph edges. Copied rather than shared because sharing it
// needs a module (KMP source sets cannot host test fixtures), which two callers do not yet
// justify; a third one does. Raise either number only with a new CI diff image as evidence, and
// in both copies.
fun oltreRoborazziOptions(): RoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.007f),
        resultValidator = ThresholdValidator(0.08f),
    ),
)
