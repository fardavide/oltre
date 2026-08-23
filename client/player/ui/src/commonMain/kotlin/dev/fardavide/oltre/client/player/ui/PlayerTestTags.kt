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

    // On the notice's own node in both of its states — which is to say, on the node when it exists
    // and on nothing when it does not. A container tag that meant something different in each state
    // would make "is the notice showing" a question about the tree's shape.
    //
    // The notice is no longer *on* the strip — it is drawn above the tab bar by whoever placed the
    // strip — and the tag stays here with the composable that draws it.
    const val NOTICE = "player-notice"

    // **The edge under the strip, and it carries a tag because nothing else can find it.** It has no
    // text, no role and no bounds of its own worth asserting on — what a test asks it is whether it
    // is there at all, now that it is the same colour and nearly the same shape as the hairline it
    // replaced.
    const val EXPERIENCE = "player-experience"
}
