package dev.fardavide.oltre.core

// The bell beside Dispatch, and the third of the game's three asks. It is the simplest of them and
// the odd one out for the same reason: `toggleAlert` has to read the row to know which of two
// questions a tap means, and `cycleHullAlert` has to check that the yard still holds the type — both
// because the thing being asked about is already in flight and may have landed between the draw and
// the tap. Nothing here is in flight yet, so there is nothing to guard against.
//
// **What it moves is the control, not the ask.** The ask is written by `startRun` and `startSurvey`,
// onto the job they create, from whatever this says at the instant the verb is tapped — see
// `GameState.announceFlights` and `FleetRun.announced`. That is what makes a flight already in the
// air deaf to a later tap, which is the promise the sheet made when it sent it.
fun toggleFlightAlerts(state: GameState): GameState = state.copy(announceFlights = !state.announceFlights)
