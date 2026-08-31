package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkTerminus

// **Threshold: a world, and something that has already left it.** It is the app icon's own idea at
// glyph scale — the lit curve of a world with one trajectory rising past it into empty space —
// reduced to the three primitives a Canvas can carry, in the icon set's own 24-unit box at the icon
// set's own stroke.
//
// **The trajectory does not touch the limb, and that gap is the drawing.** The first cut of this ran
// the line out of the circle's edge, which is a stalk, and a circle with a stalk is a magnifier — the
// galaxy brief already has one of those. It only became visible when the mark was rendered rather
// than described, which is most of the argument for rendering things. `PlayerMarkTest` walks the one
// ray where the two could meet and fails if the gap closes; `MarkPartTest` now walks the same ray for
// all twelve pairings a player can assemble, because the mistake is available to every one of them.
//
// **This used to say "nothing here varies with the save", and the day it named has come.** The old
// argument was that a mark generated from the galaxy seed would assert an identity the save cannot
// back — nobody chose the seed — and that variation would buy a generator, a property test and a
// baseline per variant for a slice whose level was zero. Both halves are answered rather than
// overturned: *A Name You Chose* makes the mark something the player picks rather than something
// derived, so there is an identity behind it; and the cost is paid in geometry assertions rather than
// in baselines, which is what `MarkPresetTest` and `MarkPartTest` are. What is left of the old note is
// the part that was always true — a mark should be *drawn for* the thing it identifies — and forty of
// them now are. See `IdentityMark` for the entry point that draws whichever one was chosen.
//
// **The drawing survives as `THRESHOLD`'s**, rather than being copied into `MarkPresets` under a new
// name: `PlayerMarkTest` walks this exact function, which is the oldest assertion in the module and
// the one that caught the magnifier, and a second function with the same geometry would be two things
// to keep in step and one test between them.
//
// **The composable that used to sit here is gone**, and its own justification is what removed it. It
// said it was *"the mark the strip wears before a player has chosen anything, so this composable still
// has a caller"* — and the strip draws `IdentityMark` now, because what it wears is whichever of forty
// marks the account holds. A `Canvas` nothing calls is not merely dead: it is a body the unit pass
// cannot enter and the screenshot and behaviour passes cannot reach, which is lines the coverage gate
// counts against every later change. What is left is the drawing itself, which has three callers.
//
// **`THRESHOLD` drawn out of its own three parts**, which is `MarkPreset.asComposed()` said in
// geometry: it is the one preset the composer can make — a limb, a rising path and a dot — and the
// protocol says so in as many words. Two copies of one shape would be two things to keep in step, and
// the frame's coordinates for the preset and for the three parts are the same coordinates.
//
// A plain `DrawScope` function rather than a body inside a `Canvas { }` lambda, for the reason
// `IdentityMark` gives: the lambda is not `@Composable`, so the unit pass counts it and cannot reach
// it.
internal fun DrawScope.drawPlayerMark(unit: Float, dx: Float, dy: Float, color: Color) {
    drawMarkBody(body = MarkBody.LIMB, unit = unit, dx = dx, dy = dy, color = color)
    drawMarkPath(path = MarkPath.RISING, unit = unit, dx = dx, dy = dy, color = color)
    drawMarkTerminus(terminus = MarkTerminus.DOT, unit = unit, dx = dx, dy = dy, color = color)
}

// 20dp: the tallest thing in a 38dp strip, and what sets the strip's height once the rail's own 9dp of
// vertical padding is matched above and below it. It stays here rather than moving to `MarkGeometry`
// with the box's own numbers, because it is a fact about the strip and not about the drawing — the
// same mark is 24, 34 and 72dp elsewhere.
internal val MARK_SIZE = 20.dp
