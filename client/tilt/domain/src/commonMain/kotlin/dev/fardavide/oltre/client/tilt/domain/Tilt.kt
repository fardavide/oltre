package dev.fardavide.oltre.client.tilt.domain

// How far the sky is pushed, as a fraction of full travel on each axis: −1..1 on both, and `NONE`
// when the device is level with however it is being held.
//
// A fraction rather than a distance, because the thing that reads it is a field of stars laid out
// in fractions of the box it fills. A number in dp here would be a second place the layout is
// decided, and the two would disagree the first time either moved.
data class Tilt(val x: Float, val y: Float) {

    companion object {

        // What a platform with no motion sensor reports, what the field shows before the first
        // sample arrives, and what a player who has asked for less motion gets. A value rather than
        // an absent one, so every platform wires a source and the ones without a sensor are an
        // answer rather than a special case — the same shape the desktop `ShakeDetector` takes.
        val NONE: Tilt = Tilt(x = 0f, y = 0f)
    }
}
