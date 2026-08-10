package dev.fardavide.oltre.client.tilt.domain

// How far the sky has been pushed, in units of full travel on each axis, and `NONE` for a device
// that has not moved since the app opened.
//
// A multiple of a distance rather than a distance, because the thing that reads it is a field of
// stars laid out in fractions of the box it fills. A number in dp here would be a second place the
// layout is decided, and the two would disagree the first time either moved.
//
// **Unbounded, and that is the contract rather than an oversight.** It was `-1..1` until 0.4.3,
// clamped by `TiltMonitor` at twelve degrees of turn, and the clamp is what made the effect feel as
// though it had an edge a little way out — every movement past a small wrist flick arrived at the
// same place. One unit is now a scale and nothing stops at it: a phone turned right round reports
// thirty, and the field it feeds takes its shift modulo the box, so a value of any size lands
// somewhere a viewer can see. Anything reading this must wrap rather than clamp — `Starfield` does,
// and `StarfieldTest` walks leans far larger than any turn can produce for exactly that reason.
data class Tilt(val x: Float, val y: Float) {

    companion object {

        // What a platform with no motion sensor reports, what the field shows before the first
        // sample arrives, and what a player who has asked for less motion gets. A value rather than
        // an absent one, so every platform wires a source and the ones without a sensor are an
        // answer rather than a special case — the same shape the desktop `ShakeDetector` takes.
        val NONE: Tilt = Tilt(x = 0f, y = 0f)
    }
}
