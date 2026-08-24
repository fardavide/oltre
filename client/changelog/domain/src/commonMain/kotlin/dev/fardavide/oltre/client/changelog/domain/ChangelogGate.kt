package dev.fardavide.oltre.client.changelog.domain

// **Whether this launch has anything new to say.** The whole of "it must open on game updated"
// (Davide, 2026-08-23), and the only rule this feature has — everything else in it is copy or a
// drawing. `.claude/docs/changelog-sheet.md` §2 is the table below in prose.
//
// A function rather than a class because it holds nothing: the two versions and the colony are the
// entire input, which is what makes every branch of it a line in a test rather than a scenario.
//
// **`hasColony` is the third input and it is load-bearing exactly once.** On the release that adds
// this feature nobody has a remembered version — every player alive and every fresh install look
// identical from here — and the two must not be answered the same way. A player coming from 0.18
// should be told what changed; somebody opening the game for the first time has nothing to be told
// changed, and would be handed sixty-five pages of history before the first mine. The save file is
// the only thing that separates them.
fun shouldOpenChangelog(
    lastSeen: ReleaseVersion?,
    current: ReleaseVersion,
    hasColony: Boolean,
): Boolean = when (lastSeen) {
    null -> hasColony
    // Inequality rather than `<`, so a downgrade opens too. TestFlight hands out older builds and a
    // restored backup can carry one; a rule that quietly did nothing in that case would be a rule
    // nobody could tell apart from a bug.
    else -> lastSeen != current
}
