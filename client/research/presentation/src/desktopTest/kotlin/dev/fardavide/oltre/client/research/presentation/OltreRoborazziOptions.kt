package dev.fardavide.oltre.client.research.presentation

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator

// The same tolerance :client:colony:presentation calibrated against CI diff artifacts — a
// baseline recorded on macOS and verified on Linux differs by ±1/255 across gradient fills and by
// ≥10/255 on a few percent of pixels at glyph edges. Raise either number only with a new CI diff
// image as evidence, and in every copy.
//
// This is the THIRD copy (with :client:shell), which is the threshold decisions.md set for
// extracting it into a module — KMP source sets cannot host test fixtures, so sharing it needs
// one. Deliberately not done in this slice: it is a build-layout change that has nothing to do
// with research, and it deserves its own PR rather than a rider on this one.
fun oltreRoborazziOptions(): RoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.007f),
        resultValidator = ThresholdValidator(0.08f),
    ),
)
