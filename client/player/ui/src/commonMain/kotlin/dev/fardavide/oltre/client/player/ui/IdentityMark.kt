package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import dev.fardavide.oltre.protocol.PlayerMark

// **Whatever the player chose, drawn.** One composable and one `DrawScope` function for both kinds of
// mark, because a caller that has a `PlayerMark` should not have to know whether it is one of the six
// or one of the forty — the sealed pair is the wire's business and the picture is this module's.
//
// **`size` is a parameter here, and that is not a relaxation of the rule `PlayerMark` states.** That
// rule is about a glyph with one caller: a defaulted parameter nothing overrides is untested surface
// bought for nothing, and a constant says the same thing and cannot be passed wrongly. This one has
// four callers at four sizes and the frame names all of them — 20dp on the strip, 24dp in a grid
// cell, 34dp on a chip and 72dp on the preview card — so the constant would have to be four
// constants, and a caller picking the wrong one is exactly the mistake a parameter cannot make.
// Required rather than defaulted, so nobody inherits a size by accident.
//
// `@NonRestartableComposable` because it is a leaf that draws its arguments and holds nothing: a
// restart scope of its own could do nothing its caller's cannot, and Compose generates one — with a
// skippability branch per parameter — unless told not to. See the `test-coverage` skill.
@Composable
@NonRestartableComposable
internal fun IdentityMark(mark: PlayerMark, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        // `this.size` and not `size`: the parameter above shadows the draw scope's own, which is the
        // one thing this signature costs.
        drawIdentityMark(mark = mark, unit = this.size.width / MARK_VIEWBOX, dx = 0f, dy = 0f, color = color)
    }
}

// **A plain `DrawScope` function rather than a body inside the `Canvas { }` lambda**, which is what
// makes the geometry reachable from a test at all. The lambda is not `@Composable`, so the unit pass
// counts it and cannot execute it — issue #99's shape, and the reason every glyph in this module is
// written this way.
//
// **No `else` on either arm**, and that is the only mechanism a set of pictures has: a seventh preset
// or a fifth body added to the wire fails to compile here until somebody has drawn it, rather than
// shipping as a silently blank mark. A blank mark is the worst outcome available — the player picked
// something and got nothing, with no way to say what went wrong.
internal fun DrawScope.drawIdentityMark(mark: PlayerMark, unit: Float, dx: Float, dy: Float, color: Color) {
    when (mark) {
        is PlayerMark.Preset -> drawMarkPreset(preset = mark.preset, unit = unit, dx = dx, dy = dy, color = color)
        // Three slots, three drawings, one on top of another — and they cannot overlap, because each
        // is confined to its own region of the box. See `MarkParts`.
        is PlayerMark.Composed -> {
            drawMarkBody(body = mark.body, unit = unit, dx = dx, dy = dy, color = color)
            drawMarkPath(path = mark.path, unit = unit, dx = dx, dy = dy, color = color)
            drawMarkTerminus(terminus = mark.terminus, unit = unit, dx = dx, dy = dy, color = color)
        }
    }
}
