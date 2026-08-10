package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.colony.presentation.displayName
import dev.fardavide.oltre.client.research.presentation.displayName
import dev.fardavide.oltre.core.WatchTarget

// "watching Metal Mine" — the clause both destinations print beside their section heading, and the
// only string in the app that has to name a row on a screen it is not being read from.
//
// **In the shell rather than in either feature, and that is the whole reason it exists here.** One
// watch is shared by the facilities, the technologies and the ladders, so tapping a square on
// Research takes the watch off a row on Colony — on a screen the player is not looking at. The
// answer is that both headings say which row holds it, which means both need a name that may belong
// to the other feature. The shell is the one module that sees both.
//
// The names are the rows' own — taken from the two mappers rather than restated, so this cannot
// drift from the cards it is about — and therefore the short forms: "Deuterium Synth." rather than
// the lock screen's "Deuterium Synthesizer", because this sits at the end of a heading that has to
// survive a 320dp Slide Over pane.
internal fun WatchTarget.watchingLabel(): String = when (this) {
    is WatchTarget.Facility -> "watching ${building.displayName()}"
    is WatchTarget.Project -> "watching ${technology.displayName()}"
    is WatchTarget.Ladder -> "watching ${technology.displayName()}"
}
