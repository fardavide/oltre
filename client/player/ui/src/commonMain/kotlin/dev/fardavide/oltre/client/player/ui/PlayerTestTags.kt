package dev.fardavide.oltre.client.player.ui

// **Public rather than internal, and for one assertion in the composition root** — `ColonyTestTags`
// is public on the same grounds and the reason here is its mirror image. The shell's `AppRobot`
// counts how many *rows* read `LV 0`, because that count is the only unambiguous way to tell a row
// holding its old level from one that has already taken its new one. This strip draws a level badge
// too, and it is not a row: without a handle on the strip, the count would silently include a piece
// of chrome and an assertion about the research branch's size would move whenever the player's did.
object PlayerTestTags {

    const val CONTENT = "player-content"

    // The gear carries no text, so it is the one control here that cannot be found any other way.
    const val SETTINGS = "player-settings"

    // **The left cluster, and it needs a handle for the opposite reason the gear does.** The gear has
    // no words; this one has all of them, and a robot reaching it by the name it draws would be
    // pressing whichever node the merge happened to leave that text on. A tag names the target
    // itself, so the tap a test makes is the tap a finger makes.
    const val PROFILE = "player-profile"

    // **The mark, tagged so a test can read the pixels rather than the tree.** A drawing has no
    // semantics — there is no text on it and no role — so the only way to ask *which* mark the strip
    // was handed is to look at the ink, and looking needs a node to capture. See
    // `PlayerRobot.inkOnTheMark`.
    const val MARK = "player-mark"

    // **The edge under the strip, and it carries a tag because nothing else can find it.** It has no
    // text, no role and no bounds of its own worth asserting on — what a test asks it is whether it
    // is there at all, now that it is the same colour and nearly the same shape as the hairline it
    // replaced.
    const val EXPERIENCE = "player-experience"
}
