package dev.fardavide.oltre.client.tilt.data

import dev.fardavide.oltre.client.tilt.domain.Tilt
import kotlinx.coroutines.flow.flowOf

// A laptop does not get tilted, and a monitor does not get tilted at all. Desktop is also the
// platform every screenshot baseline is recorded on, which turns this from a shrug into the load-
// bearing half of the whole slice: **a source that only ever reports `Tilt.NONE` is what lets the
// starfield gain a second input without a single one of the repository's forty-one baselines
// moving.** The tilt terms in `Starfield` are multiplications by zero here, so the draw calls the
// recorder sees are the ones it saw before this existed.
//
// One value and then silence rather than an empty flow, so the field is explicitly level rather
// than merely never told otherwise — the same reason the desktop `ShakeDetector` is a flow that
// exists and never emits rather than an absent implementation.
actual fun defaultTiltSource(): TiltSource = TiltSource { flowOf(Tilt.NONE) }
