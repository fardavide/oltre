package dev.fardavide.oltre.client.dispatch.ui

import dev.fardavide.oltre.core.ResourceKind

// The sheet's own handles, split out of `GalaxyTestTags` when the sheet stopped belonging to the
// Galaxy tab. **The names lost their `galaxy-` stem with it**, which is the point rather than tidying:
// a tag reading `galaxy-dispatch-send` on a sheet raised from Fleets would name the wrong screen, and
// a tag is the one piece of a test that says out loud what it is aiming at.
object DispatchTestTags {

    // On the *contents* rather than on the chrome, so it names the same thing whether a test is
    // driving the real sheet or the contents on their own — `ColonyTestTags.SHEET` is the precedent.
    // There is no tag for the scrim: the sheet stopped drawing one when it became an
    // `OltreBottomSheet`, and Material's scrim is the platform's to test, not ours.
    const val SHEET = "dispatch-sheet"

    // The verb, present only in the offer state. Absent in every refusal, which is the assertion: a
    // screen that never offers a run the model would refuse is one where this tag is missing exactly
    // when `startRun` would say no.
    const val SEND = "dispatch-send"

    // The refusal's own action, which is a different verb in each of the two refusals — a probe where
    // the world is unsurveyed, a countdown where every hull is away. One tag, because what a test
    // wants to know is *what the refusal offers*, not which refusal produced it.
    const val SHEET_ACTION = "dispatch-sheet-action"

    const val SHIPS_MORE = "dispatch-ships-more"
    const val SHIPS_FEWER = "dispatch-ships-fewer"

    // Keyed by the resource rather than by the label: "Metal" is a string the copy owns and
    // `ResourceKind.METAL` is the thing the run actually carries.
    fun gather(kind: ResourceKind): String = "dispatch-gather-${kind.name.lowercase()}"

    // Keyed by the rung's own duration in whole minutes, not by the label it prints: a ladder
    // *narrows* on a distant target rather than disabling rungs, so the rung at index 0 is a
    // different window depending on how far away the world is.
    fun window(minutes: Long): String = "dispatch-window-$minutes"
}
